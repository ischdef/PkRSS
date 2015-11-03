package com.pkmmte.pkrss.parser;

import android.net.Uri;
import android.text.Html;
import android.util.Log;
import com.pkmmte.pkrss.Article;
import com.pkmmte.pkrss.Channel;
import com.pkmmte.pkrss.Enclosure;
import com.pkmmte.pkrss.ParsedFeed;
import com.pkmmte.pkrss.PkRSS;

import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Custom PkRSS parser for parsing feeds using the RSS2 standard format.
 * This is the default parser. Use {@link PkRSS.Builder} to apply your own custom parser
 * or modify an existing one.
 */
public class Rss2Parser extends Parser {
	private final ParsedFeed parsedFeed = new ParsedFeed();
	private final DateFormat dateFormatIso8601;
	private final DateFormat dateFormatIso8601Short;
	private final DateFormat dateFormatUTC;
	private final Pattern pattern;
	private final XmlPullParser xmlParser;

	public Rss2Parser(XmlPullParser xmlParser) {
		// Initialize DateFormat object with the default date formatting
		this.dateFormatIso8601 = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
		this.dateFormatIso8601.setTimeZone(Calendar.getInstance().getTimeZone());
		this.dateFormatIso8601Short = new SimpleDateFormat("EEE, d MMM yy HH:mm:ss Z", Locale.ENGLISH);
		this.dateFormatIso8601Short.setTimeZone(Calendar.getInstance().getTimeZone());
		this.dateFormatUTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH);
		this.dateFormatUTC.setTimeZone(Calendar.getInstance().getTimeZone());

		this.pattern = Pattern.compile("-\\d{1,4}x\\d{1,4}");
		this.xmlParser = xmlParser;
	}

	@Override
	public ParsedFeed parse() {
		// Clear previous feed and start timing execution time
		parsedFeed.clear();
		long time = System.currentTimeMillis();

		// Get channel shortcut and set text encoding
		Channel channel = parsedFeed.getChannel();

		boolean insideChannel = true;
		boolean insideArticle = false;
		boolean insideChannelImage = false;
		try {
			// Reuse Article object and event holder
			Article article = new Article();
			int eventType = xmlParser.getEventType();

			// Loop through the entire xml feed
			while (eventType != XmlPullParser.END_DOCUMENT) {
				String tagName = xmlParser.getName();
				switch (eventType) {
					case XmlPullParser.START_TAG:
						if (tagName.equalsIgnoreCase("channel")) {
							// Start of channel information
							insideChannel = true;
							break;
						}
						else if (insideChannel) {
							if (tagName.equalsIgnoreCase("item")) {
								insideArticle = true;
								article = new Article();
							} else if (tagName.equalsIgnoreCase("image")) {
								insideChannelImage = true;
							} else if (insideChannelImage) {
								if (tagName.equalsIgnoreCase("url")) {
									// get next element which should be the text element
									if (xmlParser.next() == XmlPullParser.TEXT) {
										final String tagValue = xmlParser.getText();
										if (tagValue != null) {
											channel.setImage(Uri.parse(tagValue));
										}
									}
								}
							} else if (insideArticle) {
								if (tagName.equalsIgnoreCase("enclosure")) {
									// Enclosures not readable as text by XmlPullParser in Android and will fail in handleNode, considered not a bug
									// https://code.google.com/p/android/issues/detail?id=18658
									article.setEnclosure(new Enclosure(xmlParser));
								} else {
									// Parse article
									handleNode(tagName, article);
								}
							} else {
								// get next element which should be the text element
								if (xmlParser.next() == XmlPullParser.TEXT) {
									final String tagValue = xmlParser.getText();
									if (tagValue != null) {
										if (tagName.equalsIgnoreCase("title")) {
											channel.setTitle(tagValue);
										} else if (tagName.equalsIgnoreCase("description")) {
											channel.setDescription(tagValue);
										} else if (tagName.equalsIgnoreCase("language")) {
											channel.setLanguage(tagValue);
										} else if (tagName.equalsIgnoreCase("link")) {
											channel.setLink(Uri.parse(tagValue));
										}
									}
								}
							}
						}
						break;
					case XmlPullParser.END_TAG:
						if (insideChannel) {
							if (tagName.equalsIgnoreCase("channel")) {
								insideChannel = false;
								insideChannelImage = false;
								insideArticle = false;
							} else if (insideChannelImage && tagName.equalsIgnoreCase("image")) {
								insideChannelImage = false;
							} else if (insideArticle && tagName.equalsIgnoreCase("item")) {
								insideArticle = false;

								// Generate ID
								article.setId(Math.abs(article.hashCode()));

								// Remove content thumbnail
								if (article.getImage() != null && article.getContent() != null)
									article.setContent(article.getContent().replaceFirst("<img.+?>", ""));

								// (Optional) Log a minimized version of the toString() output
								log(TAG, article.toShortString(), Log.INFO);

								// Add article object to list
								parsedFeed.addArticle(article);
							}
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
		log(TAG, "Aticles parsing took " + (System.currentTimeMillis() - time) + "ms");
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
			if(xmlParser.next() != XmlPullParser.TEXT)
				return false;

			if (tag.equalsIgnoreCase("link"))
				article.setSource(Uri.parse(xmlParser.getText()));
			else if (tag.equalsIgnoreCase("title"))
				article.setTitle(xmlParser.getText());
			else if (tag.equalsIgnoreCase("description")) {
				String encoded = xmlParser.getText();
				article.setImage(Uri.parse(pullImageLink(encoded)));
				article.setDescription(Html.fromHtml(encoded.replaceAll("<img.+?>", "")).toString());
			}
			else if (tag.equalsIgnoreCase("content:encoded"))
				article.setContent(xmlParser.getText().replaceAll("[<](/)?div[^>]*[>]", ""));
			else if (tag.equalsIgnoreCase("wfw:commentRss"))
				article.setComments(xmlParser.getText());
			else if (tag.equalsIgnoreCase("category"))
				article.setNewTag(xmlParser.getText());
			else if (tag.equalsIgnoreCase("dc:creator"))
				article.setAuthor(xmlParser.getText());
			else if ((tag.equalsIgnoreCase("pubDate")) || (tag.equalsIgnoreCase("a10:updated")))
				article.setDate(getParsedDate(xmlParser.getText()));

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
	 * Converts a date to a long value.
	 * @param encodedDate The encoded date which to convert.
	 * @return A long value for the passed date String or 0 if improperly parsed.
	 */
	private long getParsedDate(String encodedDate) {
		Long returnTime = 0L;

		// First check if ISO8601 format
		try {
			returnTime = dateFormatIso8601.parse(dateFormatIso8601.format(dateFormatIso8601.parseObject(encodedDate))).getTime();
		} catch (ParseException e) {
			// check again with YY format
		}

		// Check if YY instead of YYYY used for representing year
		if (returnTime <= 0) {
			try {
				return dateFormatIso8601.parse(dateFormatIso8601.format(dateFormatIso8601Short.parseObject(encodedDate))).getTime();
			} catch (ParseException e) {
				log(TAG, "Error parsing ISO8601 date " + encodedDate, Log.WARN);
			}
		}

		// Try parsing as UTC time format if ISO parsing failed
		try {
			return dateFormatUTC.parse(dateFormatUTC.format(dateFormatUTC.parseObject(encodedDate))).getTime();
		} catch (ParseException e) {
			log(TAG, "Error parsing UTC date" + encodedDate, Log.WARN);
		}
		return returnTime;
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

			xpp.setInput(new StringReader(encoded));
			int eventType = xpp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG && "img".equals(xpp.getName())) {
					int count = xpp.getAttributeCount();
					for (int x = 0; x < count; x++) {
						if (xpp.getAttributeName(x).equalsIgnoreCase("src"))
							return pattern.matcher(xpp.getAttributeValue(x)).replaceAll("");
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
