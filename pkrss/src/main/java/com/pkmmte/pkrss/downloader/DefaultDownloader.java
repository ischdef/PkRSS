package com.pkmmte.pkrss.downloader;

import android.content.Context;
import android.net.Uri;
import android.net.http.HttpResponseCache;
import android.util.Log;
import com.pkmmte.pkrss.Request;
import com.pkmmte.pkrss.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The Default Downloader object used for general purposes.
 * <p>
 * This Downloader class uses Android's built-in HttpUrlConnection for
 * networking. It is recommended to use the OkHttpDownloader instead as
 * it is more stable and potentially performs better.
 */
public class DefaultDownloader extends Downloader {
	// OkHttpClient & configuration
	private final File cacheDir;
	private final int cacheSize = 1024 * 1024;
	private final int cacheMaxAge = 2 * 60 * 60;
	private final long connectTimeout = 15000;
	private final long readTimeout = 45000;
	private HttpURLConnection connection;

	public DefaultDownloader(Context context)  {
		cacheDir = new File(context.getCacheDir(), "http");
		try {
			HttpResponseCache.install(cacheDir, cacheSize);
		}
		catch (IOException e) {
			Log.i(TAG, "HTTP response cache installation failed:" + e);
		}
	}

	@Override
	public boolean clearCache() {
		return Utils.deleteDir(cacheDir);
	}

	@Override
	public InputStream getStream(Request request) throws IllegalArgumentException, IOException {
		// Invalid URLs are a big no no
		if (request.url == null || request.url.isEmpty()) {
			throw new IllegalArgumentException("Invalid URL!");
		}

		// Handle cache
		int maxCacheAge = request.skipCache ? 0 : cacheMaxAge;

		// Build proper URL
		String requestUrl = toUrl(request);
		URL url = new URL(requestUrl);

		// Open a connection and configure timeouts/cache
		connection = (HttpURLConnection) url.openConnection();
		connection.setRequestProperty("Cache-Control", "public, max-age=" + maxCacheAge);
		connection.setConnectTimeout((int) connectTimeout);
		connection.setReadTimeout((int) readTimeout);

		return new BufferedInputStream(connection.getInputStream());
	}

	@Override
	public void closeConnection() {
		connection.disconnect();
	}

	@Override
	public String toSafeUrl(Request request) {
		// Copy base url
		String url = request.url;

		if (request.individual) {
			// Append feed URL if individual article
			url += "feed/?withoutcomments=1";
		}
		else if (request.search != null) {
			// Append search query if available and not individual
			url += "?s=" + Uri.encode(request.search);
		}

		// Return safe url
		return url;
	}

	@Override
	public String toUrl(Request request) {
		// Copy base url
		String url = request.url;

		if (request.individual) {
			// Handle individual urls differently
			url += "feed/?withoutcomments=1";
		}
		else {
			if (request.search != null)
				url += "?s=" + Uri.encode(request.search);
			if (request.page > 1)
				url += (request.search == null ? "?paged=" : "&paged=") + String.valueOf(request.page);
		}

		// Return safe url
		return url;
	}
}
