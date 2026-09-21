package com.davemorrissey.labs.subscaleview.decoder

public class UnsupportedUriException(
	public val uri: String,
) : IllegalArgumentException("Unsupported uri: $uri")
