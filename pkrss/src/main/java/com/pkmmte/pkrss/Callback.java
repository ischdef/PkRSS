package com.pkmmte.pkrss;

import java.util.List;

public interface Callback {
	public void OnPreLoad();
	public void OnLoaded(ParsedFeed feed);
	public void OnLoadFailed();
}