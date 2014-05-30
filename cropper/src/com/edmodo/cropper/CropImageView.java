/*
 * Copyright 2013, Edmodo, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License.
 * You may obtain a copy of the License in the LICENSE file, or at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.edmodo.cropper;

import java.io.InputStream;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.edmodo.cropper.cropwindow.CropOverlayView;
import com.edmodo.cropper.cropwindow.edge.Edge;
import com.edmodo.cropper.util.ImageViewUtil;

/**
 * Custom view that provides cropping capabilities to an image.
 */
public class CropImageView extends FrameLayout {

	// Private Constants ///////////////////////////////////////////////////////

	private static final Rect EMPTY_RECT = new Rect();

	private CropType cropType = CropType.RECT;

	public enum CropType {
		RECT, OVAL, MASK;
	}

	// Member Variables ////////////////////////////////////////////////////////

	// Sets the default image guidelines to show when resizing
	public static final int DEFAULT_GUIDELINES = 0;
	public static final boolean DEFAULT_FIXED_ASPECT_RATIO = false;
	public static final int DEFAULT_ASPECT_RATIO_X = 1;
	public static final int DEFAULT_ASPECT_RATIO_Y = 1;

	private static final int DEFAULT_IMAGE_RESOURCE = 0;

	private static final String DEGREES_ROTATED = "DEGREES_ROTATED";

	private ImageView mImageView;
	private CropOverlayView mCropOverlayView;

	private ScaleType mScaleType = ScaleType.FIT_CENTER;

	private Bitmap mBitmap;
	private Bitmap mMask;
	private int mDegreesRotated = 0;

	private int mLayoutWidth;
	private int mLayoutHeight;

	// Instance variables for customizable attributes
	private int mGuidelines = DEFAULT_GUIDELINES;
	private boolean mFixAspectRatio = DEFAULT_FIXED_ASPECT_RATIO;
	private int mAspectRatioX = DEFAULT_ASPECT_RATIO_X;
	private int mAspectRatioY = DEFAULT_ASPECT_RATIO_Y;
	private int mImageResource = DEFAULT_IMAGE_RESOURCE;

	// Constructors ////////////////////////////////////////////////////////////

	public CropImageView(Context context) {
		super(context);
		init(context);
	}

	public CropImageView(Context context, AttributeSet attrs) {
		super(context, attrs);

		TypedArray ta = context.obtainStyledAttributes(attrs,
				R.styleable.CropImageView, 0, 0);

		try {
			mGuidelines = ta.getInteger(R.styleable.CropImageView_guidelines,
					DEFAULT_GUIDELINES);
			mFixAspectRatio = ta.getBoolean(
					R.styleable.CropImageView_fixAspectRatio,
					DEFAULT_FIXED_ASPECT_RATIO);
			mAspectRatioX = ta.getInteger(
					R.styleable.CropImageView_aspectRatioX,
					DEFAULT_ASPECT_RATIO_X);
			mAspectRatioY = ta.getInteger(
					R.styleable.CropImageView_aspectRatioY,
					DEFAULT_ASPECT_RATIO_Y);
			mImageResource = ta.getResourceId(
					R.styleable.CropImageView_imageResource,
					DEFAULT_IMAGE_RESOURCE);
			int scaleTypeIndex = ta.getInt(R.styleable.CropImageView_scaleType,
					ScaleType.FIT_CENTER.ordinal());
			setScaleType(scaleTypeIndex);

		} finally {
			ta.recycle();
		}

		init(context);
	}

	// View Methods ////////////////////////////////////////////////////////////

	@Override
	public Parcelable onSaveInstanceState() {

		final Bundle bundle = new Bundle();

		bundle.putParcelable("instanceState", super.onSaveInstanceState());
		bundle.putInt(DEGREES_ROTATED, mDegreesRotated);

		return bundle;

	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {

		if (state instanceof Bundle) {

			final Bundle bundle = (Bundle) state;

			if (mBitmap != null) {
				// Fixes the rotation of the image when orientation changes.
				mDegreesRotated = bundle.getInt(DEGREES_ROTATED);
				int tempDegrees = mDegreesRotated;
				rotateImage(mDegreesRotated);
				mDegreesRotated = tempDegrees;
			}

			super.onRestoreInstanceState(bundle.getParcelable("instanceState"));

		} else {
			super.onRestoreInstanceState(state);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {

		if (mBitmap != null) {
			final Rect bitmapRect = mScaleType == ScaleType.CENTER_INSIDE ? ImageViewUtil
					.getBitmapRectCenterInside(mBitmap, this) : ImageViewUtil
					.getBitmapRectFitCenter(mBitmap, this);
			mCropOverlayView.setBitmapRect(bitmapRect);
		} else {
			mCropOverlayView.setBitmapRect(EMPTY_RECT);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		if (mBitmap != null) {

			super.onMeasure(widthMeasureSpec, heightMeasureSpec);

			// Bypasses a baffling bug when used within a ScrollView, where
			// heightSize is set to 0.
			if (heightSize == 0)
				heightSize = mBitmap.getHeight();

			SizeHolder holder;

			if (mScaleType == ScaleType.CENTER_INSIDE)
				holder = onMeasureCenterInside(widthMode, widthSize,
						heightMode, heightSize);
			else
				holder = onMeasureFitCenter(widthMode, widthSize, heightMode,
						heightSize);

			mLayoutWidth = holder.width;
			mLayoutHeight = holder.height;

			final Rect bitmapRect = mScaleType == ScaleType.CENTER_INSIDE ? ImageViewUtil
					.getBitmapRectCenterInside(mBitmap.getWidth(),
							mBitmap.getHeight(), mLayoutWidth, mLayoutHeight)
					: ImageViewUtil.getBitmapRectFitCenter(mBitmap.getWidth(),
							mBitmap.getHeight(), mLayoutWidth, mLayoutHeight);

			mCropOverlayView.setBitmapRect(bitmapRect);

			// MUST CALL THIS
			setMeasuredDimension(mLayoutWidth, mLayoutHeight);

		} else {

			mCropOverlayView.setBitmapRect(EMPTY_RECT);
			setMeasuredDimension(widthSize, heightSize);
		}
	}

	private static class SizeHolder {

		int width = 0;
		int height = 0;

		public SizeHolder(int aWidth, int anHeight) {
			this.width = aWidth;
			this.height = anHeight;
		}

		@Override
		public String toString() {
			return "SizeHolder - Width: " + this.width + " Height: "
					+ this.height;
		}

	}

	private SizeHolder onMeasureCenterInside(int widthMode, int widthSize,
			int heightMode, int heightSize) {
		SizeHolder result = new SizeHolder(0, 0);

		int desiredWidth;
		int desiredHeight;
		double viewToBitmapWidthRatio = Double.POSITIVE_INFINITY;
		double viewToBitmapHeightRatio = Double.POSITIVE_INFINITY;

		// Checks if either width or height needs to be fixed
		if (widthSize < mBitmap.getWidth()) {
			viewToBitmapWidthRatio = (double) widthSize
					/ (double) mBitmap.getWidth();
		}
		if (heightSize < mBitmap.getHeight()) {
			viewToBitmapHeightRatio = (double) heightSize
					/ (double) mBitmap.getHeight();
		}

		// If either needs to be fixed, choose smallest ratio and calculate
		// from there
		if (viewToBitmapWidthRatio != Double.POSITIVE_INFINITY
				|| viewToBitmapHeightRatio != Double.POSITIVE_INFINITY) {
			if (viewToBitmapWidthRatio <= viewToBitmapHeightRatio) {
				desiredWidth = widthSize;
				desiredHeight = (int) (mBitmap.getHeight() * viewToBitmapWidthRatio);
			} else {
				desiredHeight = heightSize;
				desiredWidth = (int) (mBitmap.getWidth() * viewToBitmapHeightRatio);
			}
		}

		// Otherwise, the picture is within frame layout bounds. Desired
		// width is
		// simply picture size
		else {
			desiredWidth = mBitmap.getWidth();
			desiredHeight = mBitmap.getHeight();
		}

		result.width = getOnMeasureSpec(widthMode, widthSize, desiredWidth);
		result.height = getOnMeasureSpec(heightMode, heightSize, desiredHeight);
		return result;
	}

	private SizeHolder onMeasureFitCenter(int widthMode, int widthSize,
			int heightMode, int heightSize) {
		SizeHolder result = new SizeHolder(0, 0);

		int desiredWidth;
		int desiredHeight;
		double bitmapToViewWidthRatio = Double.POSITIVE_INFINITY;
		double bitmapToViewHeightRatio = Double.POSITIVE_INFINITY;

		// Checks if either width or height needs to be fixed
		if (mBitmap.getWidth() < widthSize) {
			bitmapToViewWidthRatio = (double) mBitmap.getWidth()
					/ (double) widthSize;
		}
		if (mBitmap.getHeight() < heightSize) {
			bitmapToViewHeightRatio = (double) mBitmap.getHeight()
					/ (double) heightSize;
		}

		// If either needs to be fixed, choose smallest ratio and calculate
		// from there
		if (bitmapToViewWidthRatio != Double.POSITIVE_INFINITY
				|| bitmapToViewHeightRatio != Double.POSITIVE_INFINITY) {
			if (bitmapToViewWidthRatio <= bitmapToViewHeightRatio) {
				desiredHeight = heightSize;
				desiredWidth = (int) (mBitmap.getWidth() / bitmapToViewHeightRatio);
			} else {
				desiredWidth = widthSize;
				desiredHeight = (int) (mBitmap.getHeight() / bitmapToViewWidthRatio);
			}
		}

		// Otherwise, the picture is within frame layout bounds. Desired
		// width is
		// simply picture size
		else {
			desiredWidth = mBitmap.getWidth();
			desiredHeight = mBitmap.getHeight();
		}

		result.width = getOnMeasureSpec(widthMode, widthSize, desiredWidth);
		result.height = getOnMeasureSpec(heightMode, heightSize, desiredHeight);
		return result;
	}

	protected void onLayout(boolean changed, int l, int t, int r, int b) {

		super.onLayout(changed, l, t, r, b);

		if (mLayoutWidth > 0 && mLayoutHeight > 0) {
			// Gets original parameters, and creates the new parameters
			final ViewGroup.LayoutParams origparams = this.getLayoutParams();
			origparams.width = mLayoutWidth;
			origparams.height = mLayoutHeight;
			setLayoutParams(origparams);
		}
	}

	// Public Methods //////////////////////////////////////////////////////////

	/**
	 * Returns the integer of the imageResource
	 *
	 * @param int the image resource id
	 */
	public int getImageResource() {
		return mImageResource;
	}

	/**
	 * Sets a Bitmap as the content of the CropImageView.
	 *
	 * @param bitmap
	 *            the Bitmap to set
	 */
	public void setImageBitmap(Bitmap bitmap) {

		mBitmap = bitmap;
		mImageView.setImageBitmap(mBitmap);

		if (mCropOverlayView != null) {
			mCropOverlayView.resetCropOverlayView();
		}
	}

	/**
	 * Sets a Bitmap and initializes the image rotation according to the EXIT
	 * data.
	 * <p>
	 * The EXIF can be retrieved by doing the following:
	 * <code>ExifInterface exif = new ExifInterface(path);</code>
	 *
	 * @param bitmap
	 *            the original bitmap to set; if null, this
	 * @param exif
	 *            the EXIF information about this bitmap; may be null
	 */
	public void setImageBitmap(Bitmap bitmap, ExifInterface exif) {

		if (bitmap == null) {
			return;
		}

		if (exif == null) {
			setImageBitmap(bitmap);
			return;
		}

		final Matrix matrix = new Matrix();
		final int orientation = exif.getAttributeInt(
				ExifInterface.TAG_ORIENTATION, 1);
		int rotate = -1;

		switch (orientation) {
		case ExifInterface.ORIENTATION_ROTATE_270:
			rotate = 270;
			break;
		case ExifInterface.ORIENTATION_ROTATE_180:
			rotate = 180;
			break;
		case ExifInterface.ORIENTATION_ROTATE_90:
			rotate = 90;
			break;
		}

		if (rotate == -1) {
			setImageBitmap(bitmap);
		} else {
			matrix.postRotate(rotate);
			final Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
					bitmap.getWidth(), bitmap.getHeight(), matrix, true);
			setImageBitmap(rotatedBitmap);
			bitmap.recycle();
		}
	}

	/**
	 * Sets a Drawable as the content of the CropImageView.
	 *
	 * @param resId
	 *            the drawable resource ID to set
	 */
	public void setImageResource(int resId) {
		if (resId != 0) {
			Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
			setImageBitmap(bitmap);
		}
	}

	public Bitmap getCroppedImage() {
		return getCroppedImage(false);
	}

	/**
	 * Sets a stream as the content of the CropImageView
	 * 
	 * @param is
	 *            the InputStream of the wanted Bitmap
	 */
	public void setImageStream(InputStream is) {
		Bitmap bitmap = BitmapFactory.decodeStream(is);
		setImageBitmap(bitmap);
	}

	/**
	 * Gets the cropped image based on the current crop window.
	 *
	 * @return a new Bitmap representing the cropped image
	 */
	public Bitmap getCroppedImage(boolean isRect) {

		final Rect displayedImageRect = mScaleType == ScaleType.CENTER_INSIDE ? ImageViewUtil
				.getBitmapRectCenterInside(mBitmap, mImageView) : ImageViewUtil
				.getBitmapRectFitCenter(mBitmap, mImageView);

		// Get the scale factor between the actual Bitmap dimensions and the
		// displayed dimensions for width.
		final float actualImageWidth = mBitmap.getWidth();
		final float displayedImageWidth = displayedImageRect.width();
		final float scaleFactorWidth = actualImageWidth / displayedImageWidth;

		// Get the scale factor between the actual Bitmap dimensions and the
		// displayed dimensions for height.
		final float actualImageHeight = mBitmap.getHeight();
		final float displayedImageHeight = displayedImageRect.height();
		final float scaleFactorHeight = actualImageHeight
				/ displayedImageHeight;

		// Get crop window position relative to the displayed image.
		final float cropWindowX = Edge.LEFT.getCoordinate()
				- displayedImageRect.left;
		final float cropWindowY = Edge.TOP.getCoordinate()
				- displayedImageRect.top;
		final float cropWindowWidth = Edge.getWidth();
		final float cropWindowHeight = Edge.getHeight();

		// Scale the crop window position to the actual size of the Bitmap.
		final float actualCropX = cropWindowX * scaleFactorWidth;
		final float actualCropY = cropWindowY * scaleFactorHeight;
		final float actualCropWidth = cropWindowWidth * scaleFactorWidth;
		final float actualCropHeight = cropWindowHeight * scaleFactorHeight;

		// Crop the subset from the original Bitmap.
		int bmpWidth = mBitmap.getWidth();
		int bmpHeight = mBitmap.getHeight();

		int cropX = Math.min((int) actualCropX, bmpWidth);
		int cropY = Math.min((int) actualCropY, bmpHeight);
		int cropWidth = Math.min((int) actualCropWidth, bmpWidth - cropX);
		int cropHeight = Math.min((int) actualCropHeight, bmpHeight - cropY);

		final Bitmap croppedBitmap = Bitmap.createBitmap(mBitmap, cropX, cropY,
				cropWidth, cropHeight);
		CropType type = cropType;
		if (isRect) {
			type = CropType.RECT;
		}

		switch (type) {
		case RECT:
			return croppedBitmap;
		case OVAL:
			Bitmap bmp = getRoundedShape(croppedBitmap);
			if (croppedBitmap != null && !croppedBitmap.isRecycled()) {
				croppedBitmap.recycle();
			}
			return bmp;
		case MASK:
			Bitmap maskedBitmap = getMaskedShape(croppedBitmap);
			if (croppedBitmap != null && !croppedBitmap.isRecycled()) {
				croppedBitmap.recycle();
			}
			return maskedBitmap;
		default:
			break;
		}
		return croppedBitmap;
	}

	public Bitmap getRoundedShape(Bitmap scaleBitmapImage) {
		int targetWidth = scaleBitmapImage.getWidth();// 125
		int targetHeight = scaleBitmapImage.getHeight();// 125

		Bitmap targetBitmap = Bitmap.createBitmap(targetWidth, targetHeight,
				Bitmap.Config.ARGB_8888);

		Canvas canvas = new Canvas(targetBitmap);
		Path path = new Path();
		// path.addCircle(
		// ((float) targetWidth - 1) / 2,
		// ((float) targetHeight - 1) / 2,
		// (Math.min(((float) targetWidth), ((float) targetHeight)) / 2),
		// Path.Direction.CCW);
		RectF oval = new RectF(0, 0, scaleBitmapImage.getWidth(),
				scaleBitmapImage.getHeight());
		path.addOval(oval, Path.Direction.CCW);
		canvas.clipPath(path);
		Bitmap sourceBitmap = scaleBitmapImage;
		canvas.drawBitmap(sourceBitmap, new Rect(0, 0, sourceBitmap.getWidth(),
				sourceBitmap.getHeight()), new Rect(0, 0, targetWidth,
				targetHeight), new Paint());
		return targetBitmap;
	}

	public Bitmap getMaskedShape(Bitmap sourceBitmap) {

		int sourceWidth = sourceBitmap.getWidth();
		int sourceHeight = sourceBitmap.getHeight();

		Paint paint = new Paint();

		Bitmap targetBitmap = Bitmap.createBitmap(sourceWidth, sourceHeight,
				Bitmap.Config.ARGB_8888);

		Canvas canvas = new Canvas(targetBitmap);

		canvas.drawBitmap(sourceBitmap, new Rect(0, 0, sourceWidth,
				sourceHeight), new Rect(0, 0, sourceWidth, sourceHeight), paint);
		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
		canvas.drawBitmap(mMask,
				new Rect(0, 0, mMask.getWidth(), mMask.getHeight()), new RectF(0,
						0, sourceWidth, sourceHeight), paint);

		return targetBitmap;
	}

	/**
	 * Gets the crop window's position relative to the source Bitmap (not the
	 * image displayed in the CropImageView).
	 *
	 * @return a RectF instance containing cropped area boundaries of the source
	 *         Bitmap
	 */
	public RectF getActualCropRect() {

		final Rect displayedImageRect = mScaleType == ScaleType.CENTER_INSIDE ? ImageViewUtil
				.getBitmapRectCenterInside(mBitmap, mImageView) : ImageViewUtil
				.getBitmapRectFitCenter(mBitmap, mImageView);

		// Get the scale factor between the actual Bitmap dimensions and the
		// displayed dimensions for width.
		final float actualImageWidth = mBitmap.getWidth();
		final float displayedImageWidth = displayedImageRect.width();
		final float scaleFactorWidth = actualImageWidth / displayedImageWidth;

		// Get the scale factor between the actual Bitmap dimensions and the
		// displayed dimensions for height.
		final float actualImageHeight = mBitmap.getHeight();
		final float displayedImageHeight = displayedImageRect.height();
		final float scaleFactorHeight = actualImageHeight
				/ displayedImageHeight;

		// Get crop window position relative to the displayed image.
		final float displayedCropLeft = Edge.LEFT.getCoordinate()
				- displayedImageRect.left;
		final float displayedCropTop = Edge.TOP.getCoordinate()
				- displayedImageRect.top;
		final float displayedCropWidth = Edge.getWidth();
		final float displayedCropHeight = Edge.getHeight();

		// Scale the crop window position to the actual size of the Bitmap.
		float actualCropLeft = displayedCropLeft * scaleFactorWidth;
		float actualCropTop = displayedCropTop * scaleFactorHeight;
		float actualCropRight = actualCropLeft + displayedCropWidth
				* scaleFactorWidth;
		float actualCropBottom = actualCropTop + displayedCropHeight
				* scaleFactorHeight;

		// Correct for floating point errors. Crop rect boundaries should not
		// exceed the source Bitmap bounds.
		actualCropLeft = Math.max(0f, actualCropLeft);
		actualCropTop = Math.max(0f, actualCropTop);
		actualCropRight = Math.min(mBitmap.getWidth(), actualCropRight);
		actualCropBottom = Math.min(mBitmap.getHeight(), actualCropBottom);

		final RectF actualCropRect = new RectF(actualCropLeft, actualCropTop,
				actualCropRight, actualCropBottom);

		return actualCropRect;
	}

	/**
	 * Sets whether the aspect ratio is fixed or not; true fixes the aspect
	 * ratio, while false allows it to be changed.
	 *
	 * @param fixAspectRatio
	 *            Boolean that signals whether the aspect ratio should be
	 *            maintained.
	 */
	public void setFixedAspectRatio(boolean fixAspectRatio) {
		mCropOverlayView.setFixedAspectRatio(fixAspectRatio);
	}

	/**
	 * Sets the guidelines for the CropOverlayView to be either on, off, or to
	 * show when resizing the application.
	 *
	 * @param guidelines
	 *            Integer that signals whether the guidelines should be on, off,
	 *            or only showing when resizing.
	 */
	public void setGuidelines(int guidelines) {
		mCropOverlayView.setGuidelines(guidelines);
	}

	/**
	 * Sets the both the X and Y values of the aspectRatio.
	 *
	 * @param aspectRatioX
	 *            int that specifies the new X value of the aspect ratio
	 * @param aspectRatioX
	 *            int that specifies the new Y value of the aspect ratio
	 */
	public void setAspectRatio(int aspectRatioX, int aspectRatioY) {
		mAspectRatioX = aspectRatioX;
		mCropOverlayView.setAspectRatioX(mAspectRatioX);

		mAspectRatioY = aspectRatioY;
		mCropOverlayView.setAspectRatioY(mAspectRatioY);
	}

	/**
	 * Rotates image by the specified number of degrees clockwise. Cycles from 0
	 * to 360 degrees.
	 *
	 * @param degrees
	 *            Integer specifying the number of degrees to rotate.
	 */
	public void rotateImage(int degrees) {

		Matrix matrix = new Matrix();
		matrix.postRotate(degrees);
		mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(),
				mBitmap.getHeight(), matrix, true);
		setImageBitmap(mBitmap);

		mDegreesRotated += degrees;
		mDegreesRotated = mDegreesRotated % 360;
	}

	private void setScaleType(int scaleTypeIndex) {
		ScaleType[] scaleTypes = ScaleType.values();
		ScaleType scaleType = scaleTypeIndex >= 0
				&& scaleTypeIndex < scaleTypes.length ? scaleTypes[scaleTypeIndex]
				: ScaleType.CENTER_INSIDE;
		setScaleType(scaleType);
	}

	/**
	 * Controls how the image should be resized or moved to match the size of
	 * this ImageView. Valid values are {@link ScaleType#FIT_CENTER} and
	 * {@link ScaleType#CENTER_INSIDE}
	 *
	 * @param scaleType
	 *            The desired scaling mode.
	 */
	public void setScaleType(ScaleType scaleType) {
		if (scaleType != ScaleType.CENTER_INSIDE
				&& scaleType != ScaleType.FIT_CENTER)
			return;
		mScaleType = scaleType;
		if (mImageView != null)
			mImageView.setScaleType(mScaleType);
	}

	// Private Methods /////////////////////////////////////////////////////////

	private void init(Context context) {

		final LayoutInflater inflater = LayoutInflater.from(context);
		final View v = inflater.inflate(R.layout.crop_image_view, this, true);

		mImageView = (ImageView) v.findViewById(R.id.ImageView_image);
		mImageView.setScaleType(mScaleType);

		setImageResource(mImageResource);
		mCropOverlayView = (CropOverlayView) v
				.findViewById(R.id.CropOverlayView);
		mCropOverlayView.setInitialAttributeValues(mGuidelines,
				mFixAspectRatio, mAspectRatioX, mAspectRatioY);
	}

	/**
	 * Determines the specs for the onMeasure function. Calculates the width or
	 * height depending on the mode.
	 *
	 * @param measureSpecMode
	 *            The mode of the measured width or height.
	 * @param measureSpecSize
	 *            The size of the measured width or height.
	 * @param desiredSize
	 *            The desired size of the measured width or height.
	 * @return The final size of the width or height.
	 */
	private static int getOnMeasureSpec(int measureSpecMode,
			int measureSpecSize, int desiredSize) {

		// Measure Width
		int spec;
		if (measureSpecMode == MeasureSpec.EXACTLY) {
			// Must be this size
			spec = measureSpecSize;
		} else if (measureSpecMode == MeasureSpec.AT_MOST) {
			// Can't be bigger than...; match_parent value
			spec = Math.min(desiredSize, measureSpecSize);
		} else {
			// Be whatever you want; wrap_content
			spec = desiredSize;
		}

		return spec;
	}

	public CropType getCropType() {
		return cropType;
	}

	public void setCropType(CropType cropType) {
		this.cropType = cropType;
		mCropOverlayView.setCropType(cropType);
		requestLayout();
	}

	public void setBitmapMask(Bitmap maskBitmap) {
		this.cropType = CropType.MASK;
		this.mMask = maskBitmap;
		mCropOverlayView.setMaskBitmap(maskBitmap);
		mCropOverlayView.setCropType(cropType);
		requestLayout();
	}

}
