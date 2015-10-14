package com.pkmmte.pkrss;

import java.util.List;

public interface Callback {
	public void OnPreLoad();
	public void OnLoaded(Channel newChannel, List<Article> newArticles);
	public void OnLoadFailed();
}