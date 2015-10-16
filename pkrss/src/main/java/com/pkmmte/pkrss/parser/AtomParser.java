package com.pkmmte.pkrss.parser;

import android.net.Uri;
import android.text.Html;
import android.util.Log;
import com.pkmmte.pkrss.Article;
import com.pkmmte.pkrss.Channel;
import com.pkmmte.pkrss.ParsedFeed;
import com.pkmmte.pkrss.PkRSS;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Custom PkRSS parser for parsing feeds using the Atom format.
 * This is the default parser. Use {@link PkRSS.Builder} to apply your own custom parser
 * or modify an existing one.
 */
public class AtomParser extends Parser {
	private final ParsedFeed parsedFeed = new ParsedFeed();
	private final DateFormat dateFormat;
	private final Pattern pattern;
	private final XmlPullParser xmlParser;

	public AtomParser() {
		// Initialize DateFormat object with the default date formatting
		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
		dateFormat.setTimeZone(Calendar.getInstance().getTimeZone());
		pattern = Pattern.compile("-\\d{1,4}x\\d{1,4}");

		// Initialize XmlPullParser object with a common configuration
		XmlPullParser parser = null;
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(false);
			parser = factory.newPullParser();
		}
		catch (XmlPullParserException e) {
			e.printStackTrace();
		}
		xmlParser = parser;
	}

	@Override
	public ParsedFeed parse(String rssStream) {
		// Clear previous feed and start timing execution time
		parsedFeed.clear();
		long time = System.currentTimeMillis();

		// Get channel shortcut
		Channel channel = parsedFeed.getChannel();

		try {
			// Get InputStream from String and set it to our XmlPullParser
			InputStream input = new ByteArrayInputStream(rssStream.getBytes());
			xmlParser.setInput(input, null);

			// Reuse Article object and event holder
			Article article = new Article();
			int eventType = xmlParser.getEventType();
			boolean insideArticle = false;

			// Loop through the entire xml feed
			while (eventType != XmlPullParser.END_DOCUMENT) {
				String tagName = xmlParser.getName();
				switch (eventType) {
					case XmlPullParser.START_TAG:
						if (tagName.equalsIgnoreCase("feed")) {
							channel.setLanguage(xmlParser.getAttributeValue(null, "xml:lang"));
						} else if (tagName.equalsIgnoreCase("entry")) {
							insideArticle = true;
							article = new Article();
						} else if (insideArticle) {
							handleNode(tagName, article);
						} else {
							// get next element which should be the text element
							if (xmlParser.next() == XmlPullParser.TEXT) {
								final String tagValue = xmlParser.getText();
								if (tagValue != null) {
									if (tagName.equalsIgnoreCase("title")) {
										channel.setTitle(tagValue);
									} else if (tagName.equalsIgnoreCase("subtitle")) {
										channel.setDescription(tagValue);
									} else if (tagName.equalsIgnoreCase("logo")) {
										channel.setImage(Uri.parse(tagValue));
									}
								}
							}
						}
						break;
					case XmlPullParser.END_TAG:
						if (tagName.equalsIgnoreCase("entry")) {
							insideArticle = false;

							// Generate ID
							article.setId(Math.abs(article.hashCode()));

							// Remove content thumbnail
							if(article.getImage() != null && article.getContent() != null)
								article.setContent(article.getContent().replaceFirst("<img.+?>", ""));

							// (Optional) Log a minimized version of the toString() output
							log(TAG, article.toShortString(), Log.INFO);

							// Add article object to list
							parsedFeed.addArticle(article);
						}
						break;
					default:
						break;
				}
				eventType = xmlParser.next();
			}
		}
		catch (IOException e) {
			// Uh oh
			e.printStackTrace();
		}
		catch (XmlPullParserException e) {
			// Oh noes
			e.printStackTrace();
		}

		// Encoding only valid after at least one xmlParser.next() was called
		channel.setEncoding(xmlParser.getInputEncoding());

		// Output execution time and return list of newly parsed articles
		log(TAG, "Parsing took " + (System.currentTimeMillis() - time) + "ms");
		return parsedFeed;
	}

	/**
	 * Handles a node from the tag node and assigns it to the correct article value.
	 * @param tag The tag which to handle.
	 * @param article Article object to assign the node value to.
	 * @return True if a proper tag was given or handled. False if improper tag was given or
	 * if an exception if triggered.
	 */
	private boolean handleNode(String tag, Article article) {
		try {
			if (tag.equalsIgnoreCase("category"))
				article.setNewTag(xmlParser.getAttributeValue(null, "term"));
			else if (tag.equalsIgnoreCase("link")) {
				String rel = xmlParser.getAttributeValue(null, "rel");
				if (rel.equalsIgnoreCase("alternate"))
					article.setSource(Uri.parse(xmlParser.getAttributeValue(null, "href")));
				else if (rel.equalsIgnoreCase("replies"))
					article.setComments(xmlParser.getAttributeValue(null, "href"));
			}

			if(xmlParser.next() != XmlPullParser.TEXT)
				return false;

			if (tag.equalsIgnoreCase("title"))
				article.setTitle(xmlParser.getText());
			else if (tag.equalsIgnoreCase("summary")) {
				String encoded = xmlParser.getText();
				article.setImage(Uri.parse(pullImageLink(encoded)));
				article.setDescription(Html.fromHtml(encoded.replaceAll("<img.+?>", "")).toString());
			}
			else if (tag.equalsIgnoreCase("content"))
				article.setContent(xmlParser.getText().replaceAll("[<](/)?div[^>]*[>]", ""));
			else if (tag.equalsIgnoreCase("category"))
				article.setNewTag(xmlParser.getText());
			else if (tag.equalsIgnoreCase("name"))
				article.setAuthor(xmlParser.getText());
			else if (tag.equalsIgnoreCase("published")) {
				article.setDate(getParsedDate(xmlParser.getText()));
			}

			return true;
		}
		catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		catch (XmlPullParserException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Converts a date in the "EEE, d MMM yyyy HH:mm:ss Z" format to a long value.
	 * @param encodedDate The encoded date which to convert.
	 * @return A long value for the passed date String or 0 if improperly parsed.
	 */
	private long getParsedDate(String encodedDate) {
		try {
			return dateFormat.parse(dateFormat.format(dateFormat.parseObject(encodedDate.replaceAll("Z$", "+0000")))).getTime();
		}
		catch (ParseException e) {
			log(TAG, "Error parsing date " + encodedDate, Log.WARN);
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * Pulls an image URL from an encoded String.
	 *
	 * @param encoded The String which to extract an image URL from.
	 * @return The first image URL found on the encoded String. May return an
	 * empty String if none were found.
	 */
	private String pullImageLink(String encoded) {
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser xpp = factory.newPullParser();

			// Remove rubbish text before image tag starts
			encoded = encoded.replaceFirst(".*<img", "<img");

			// Add xml description
			encoded = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + "\n" + encoded;

			xpp.setInput(new StringReader(encoded));
			int eventType = xpp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG && "img".equals(xpp.getName())) {
					final String image = xpp.getAttributeValue(null, "src");
					if (image != null) {
						// check for minimum image size of 5x5 pixel
						int height;
						int width;
						try {
							height = Integer.parseInt(xpp.getAttributeValue(null, "height"));
							width = Integer.parseInt(xpp.getAttributeValue(null, "width"));
						} catch (NumberFormatException e) {
							// no size given, assume valid image size
							return image;
						}
						return (height < 5 && width < 5) ? "" : image;
					}
				}
				eventType = xpp.next();
			}
		}
		catch (Exception e) {
			log(TAG, "Error pulling image link from description!\n" + e.getMessage(), Log.WARN);
		}

		return "";
	}
}