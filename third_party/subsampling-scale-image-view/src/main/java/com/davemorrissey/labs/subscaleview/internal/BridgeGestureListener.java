package com.davemorrissey.labs.subscaleview.internal;

import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

abstract class BridgeGestureListener extends GestureDetector.SimpleOnGestureListener {

	@Override
	public boolean onFling(@Nullable MotionEvent e1, @Nullable MotionEvent e2, float velocityX, float velocityY) {
		/*
		Needs this because parameters e1 and e2 are marked as @NonNull in parent class but can actually be null
		https://stackoverflow.com/questions/52221783/how-to-disable-nonnull-nullable-annotations-in-kotlin-generated-java-code
		 */
		return super.onFling(e1, e2, velocityX, velocityY);
	}
}
