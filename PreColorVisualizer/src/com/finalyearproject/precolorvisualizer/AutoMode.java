package com.finalyearproject.precolorvisualizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

@SuppressLint("NewApi")
public class AutoMode extends Activity {
	
	/**
	 * Image View that will hold down our image with
	 * edges and actual images. 
	 */
	ImageView backView, frontView;
	
	/**
	 *  Variable that will holds down the request for camera
	 *  or gallery
	 */
	public final int GALLERY_REQUEST = 101, CAMERA_REQUEST = 102;
	
	/**
	 * Image that is selected by user
	 * Either from gallery or from Camera
	 */
	Bitmap galleryBmp;
	
	/**
	 * those are the bitmaps that will hold down the current
	 * state of the bitmap
	 * i.e.
	 * FloodFilling for different segmentations
	 * and last point where we done fillin' color.
	 */
	Bitmap flood1, cannys, flood11, holdLastPic, white, black;
	
	/**
	 * The whole layout where we are showing our images and stuff
	 * in the end we save the whole view and saving it in gallery
	 */
	RelativeLayout view;
	
	/**
	 * Initializing the replacement color
	 * that is byDefault will be shown on
	 * Color Picker Dialog
	 */
	int replacementColor = Color.GRAY;
	
	/**
	 * alert dialog holds some of the options to be chosen from user
	 * like getting image from camera or gallery etc..
	 */
	AlertDialog.Builder builder;
	
	/**
	 * showing progress when process is being executed so user can know
	 * and wait unitl process is done
	 */
	boolean showProgess = false;

	
	
	/**
	 * default onCreate Method
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.automode);
		
		//initialize views
		initializer();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.segmentation, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// getting the back image and saving in Bitmap
		backView.setDrawingCacheEnabled(true);
		Bitmap bmpToBeSegmented = backView.getDrawingCache();

		
		int id = item.getItemId();
		switch (id) {
		
		// Showing a dialog with color chooser
		case R.id.SagColPic:
			ColorPicker();
			break;
		
		// clearing edges that was drawn before
		case R.id.segClearFrontView:
			Toast.makeText(getBaseContext(), "Edges Cleared!", Toast.LENGTH_SHORT).show();
			clearEdge();
			break;
		
		// saving image in gallery
		case R.id.segSave:
			saveImage();
			break;
		
		// a dialog with two buttons, one for camera, one for gallery
		case R.id.SegGal:
			showOption();
			break;

		// applying canny
		case R.id.segCanny:

			AutoApplyCanny( bmpToBeSegmented );
			
			break;
			
		// applying laplcian
		case R.id.segLap:

			applyLaplacian( bmpToBeSegmented );
			
			break;
			
		// applying simplet threshold
		case R.id.segThreshold:
			thresholding( bmpToBeSegmented );
			break;
		
		// handle leakage of memory
		default:
			backView.setDrawingCacheEnabled( false );
			bmpToBeSegmented.recycle();
			
		}
		return super.onOptionsItemSelected(item);
	}
	
	/**
	 *  Showing a dialog with two options
	 */
	private void showOption(){

		builder = new AlertDialog.Builder(this);
		builder.setMessage("Use Image From")
				.setCancelable(true)
				.setPositiveButton("Camera",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int id) {
								//clear views before carry out
								//new one
								clearView();
								callTehCamIntent();
							}
						})
				.setNegativeButton("Gallery",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int id) {
								// clear views before
								clearView();
								callTheGalIntent();
							}
						});
		AlertDialog alert = builder.create();

		alert.show();
	}
	
	/**
	* Before we carry out new images
	* we had to clear views
	*/
	private void clearView(){
		// clear the backview holding actual image
		backView.setImageBitmap(null);
		backView.destroyDrawingCache();
		
		//clearing the front image view holding edges/segments
		frontView.setImageBitmap(null);
		frontView.destroyDrawingCache();
	}
	
	/**
	 * clear the edges
	 * @param bmp
	 */	
	private void clearEdge(){

		frontView.setDrawingCacheEnabled(true);
		white = frontView.getDrawingCache();

		white = white.copy(Config.ARGB_8888, true);
		for (int i = 0; i < white.getWidth(); i++) {
			for (int j = 0; j < white.getHeight(); j++) {
				if (white.getPixel(i, j) == Color.WHITE) {
					white.setPixel(i, j, Color.TRANSPARENT);
				}
			}
		}
		Mat m = new Mat(white.getWidth(), white.getHeight(), CvType.CV_8UC4);
		Utils.bitmapToMat(white, m);
		Imgproc.morphologyEx(m, m, Imgproc.MORPH_OPEN, new Mat());
		Utils.matToBitmap(m, white);
		frontView.setImageBitmap(white);
		frontView.setDrawingCacheEnabled(false);
		
	}
	
	/**
	 * Save image in Gallery
	 * @param bmp
	 */
	private void saveImage(){
		Toast.makeText(getBaseContext(),
				"Image Saved at/sdCard/Pictures/PreColorResults/",
				Toast.LENGTH_SHORT).show();

		Bitmap resultImage;
		resultImage = Bitmap.createBitmap(galleryBmp);
		resultImage.copy(Config.ARGB_8888, true);
		view.setDrawingCacheEnabled(true);
		resultImage = view.getDrawingCache();
		String root = Environment.getExternalStorageDirectory()
				.getAbsolutePath() + "/Pictures".toString();
		File myDir = new File(root + "/PreColorResults");

		myDir.mkdirs();

		Random generator = new Random();
		long n = 1000000;
		n = generator.nextLong();
		String fname = "Image-" + n + ".png";
		File file = new File(myDir, fname);

		Log.i("" + n, "" + file);
		if (file.exists())
			file.delete();
		try {
			FileOutputStream out = new FileOutputStream(file);
			resultImage.compress(Bitmap.CompressFormat.PNG, 100, out);
			sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED,
					Uri.parse("file://"
							+ Environment.getExternalStorageDirectory())));
			out.flush();
			out.close();
			// frontView.setDrawingCacheEnabled(false);
			view.setDrawingCacheEnabled(false);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// Intent get = getIntent();
			// finish();
			// startActivity(get);
		}
		// ProgressBar pb = new ProgressBar(getBaseContext());

	}
	
	/**
	 * it's after we applied LAPLACIAN algo
	 * Here we are just getting the coordinates of
	 * touch and filling color to it
	 * We color on those places at the front view where we
	 * see Black color. Because now frontView is in only
	 * two( Binary ) colors and we just had to supposed white color as
	 * edges..
	 * @param galleryBmp
	 */
	private void applyLaplacian( Bitmap bmp ) {
		
		Bitmap laplac = galleryBmp;
		laplac = laplac.copy(Config.ARGB_8888, true);
		laplac = applyLaplac(laplac);

		for (int i = 0; i < laplac.getWidth(); i++) {
			for (int j = 0; j < laplac.getHeight(); j++) {
				if (laplac.getPixel(i, j) == Color.BLACK) {
					laplac.setHasAlpha(true);
					laplac.setPixel(i, j, Color.TRANSPARENT);
				}
			}
		}

		frontView.setImageBitmap(laplac);
		final Bitmap flood11 = laplac.copy(Config.ARGB_8888, true);

		frontView.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View arg0, MotionEvent arg1) {
				int x, y;
				x = (int) arg1.getX();
				y = (int) arg1.getY();
				sendToFill(flood11, x, y);

				return false;
			}
		});
	}

	/**
	 * it's after we applied CANNY algo
	 * Here we are just getting the coordinates of
	 * touch and filling color to it
	 * We color on those places at the front view where we
	 * see Black color. Because now frontView is in only
	 * two( Binary ) colors and we just had to supposed white color as
	 * edges..
	 * @param galleryBmp
	 */
	private void AutoApplyCanny(Bitmap galleryBmp) {

		cannys = galleryBmp;
		cannys = cannys.copy(Config.ARGB_8888, true);
		cannys = applyCanny(cannys);

		for (int i = 0; i < cannys.getWidth(); i++) {
			for (int j = 0; j < cannys.getHeight(); j++) {
				if (cannys.getPixel(i, j) == Color.BLACK) {
					cannys.setHasAlpha(true);
					cannys.setPixel(i, j, Color.TRANSPARENT);
				}

			}
		}

		frontView.setImageBitmap(cannys);
		flood1 = cannys.copy(Config.ARGB_8888, true);

		frontView.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View arg0, MotionEvent arg1) {
				int x, y;
				x = (int) arg1.getX();
				y = (int) arg1.getY();

				sendToFill(flood1, x, y);

				return false;
			}
		});

	}
	
	/**
	 * Applying Threshold
	 * @param bm
	 * @param x
	 * @param y
	 */
	private void thresholding(Bitmap galleryBmp) {

		Bitmap localBmp = galleryBmp;
		localBmp = localBmp.copy(Config.ARGB_8888, true);
		localBmp = applyThreshold( localBmp );

		for (int i = 0; i < localBmp.getWidth(); i++) {
			for (int j = 0; j < localBmp.getHeight(); j++) {
				if (localBmp.getPixel(i, j) == Color.BLACK) {
					localBmp.setHasAlpha(true);
					localBmp.setPixel(i, j, Color.TRANSPARENT);
				}

			}
		}

		frontView.setImageBitmap(localBmp);
		flood1 = localBmp.copy(Config.ARGB_8888, true);

		frontView.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View arg0, MotionEvent arg1) {
				int x, y;
				x = (int) arg1.getX();
				y = (int) arg1.getY();

				sendToFill(flood1, x, y);

				return false;
			}
		});

	}
	
	/**
	 * Here we are drawing a selected segment with the 
	 * selected color
	 * @param bm
	 * @param x
	 * @param y
	 */

	public void sendToFill(Bitmap bm, int x, int y) {
		holdLastPic = bm.copy(Config.ARGB_8888, true);
		QueueLinearFloodFiller qlfl = new QueueLinearFloodFiller(holdLastPic);

		qlfl.useImage(holdLastPic);
		qlfl.setTargetColor(Color.TRANSPARENT);
		qlfl.setFillColor(replacementColor);
		qlfl.floodFill(x, y);
		holdLastPic = qlfl.getImage();
		frontView.setImageBitmap(holdLastPic);

		flood1 = holdLastPic;
		flood11 = holdLastPic;
		Toast.makeText(getBaseContext(), "Color Applied",
				Toast.LENGTH_SHORT - 1).show();
	}

	/**
	 *  Apply canny
	 *  which is good from others at the moment
	 * @param bmp
	 * @return Segmented Image
	 */
	private Bitmap applyCanny(Bitmap bmp) {
		Bitmap op = galleryBmp.copy(Config.ARGB_8888, true);
		Size s = new Size(3, 3);
		Mat canyMat = new Mat(op.getWidth(), op.getHeight(), CvType.CV_8UC4);
		Utils.bitmapToMat(op, canyMat);
		Imgproc.cvtColor(canyMat, canyMat, Imgproc.COLOR_RGBA2BGR);
		canyMat.convertTo(canyMat, CvType.CV_8UC4);
		Imgproc.blur(canyMat, canyMat, s);
		Imgproc.Canny(canyMat, canyMat, 50, 50);
		Imgproc.morphologyEx(canyMat, canyMat, 4, new Mat());
		Imgproc.cvtColor(canyMat, canyMat, Imgproc.COLOR_GRAY2BGRA);
		Utils.matToBitmap(canyMat, op);
		bmp = op;
		return bmp;
	}
	
	/**
	 * Applying laplacian
	 * @param bmp
	 * @return segmented Bitmap
	 */
	private Bitmap applyLaplac(Bitmap bmp) {
		Bitmap op = galleryBmp.copy(Config.ARGB_8888, true);
		Mat lapMat = new Mat(op.getWidth(), op.getHeight(), CvType.CV_8UC4);
		Utils.bitmapToMat(op, lapMat);
		Imgproc.cvtColor(lapMat, lapMat, Imgproc.COLOR_BGRA2GRAY);
		Imgproc.Laplacian(lapMat, lapMat, CvType.CV_8U);
		Imgproc.cvtColor(lapMat, lapMat, Imgproc.COLOR_GRAY2BGRA);
		Utils.matToBitmap(lapMat, op);
		bmp = op;
		
		return bmp;
	}
	
	/**
	 * applying thresholding
	 * @param bmp
	 * @return segmented bitmap
	 */
	private Bitmap applyThreshold(Bitmap bmp) {
		Bitmap op = galleryBmp.copy(Config.ARGB_8888, true);
		Mat threshMat = new Mat(op.getWidth(), op.getHeight(), CvType.CV_8UC4);
		Utils.bitmapToMat(op, threshMat);
		Imgproc.cvtColor(threshMat, threshMat, Imgproc.COLOR_BGRA2GRAY);
		Imgproc.threshold(threshMat, threshMat, 100, 255, Imgproc.THRESH_OTSU);
		Imgproc.cvtColor(threshMat, threshMat, Imgproc.COLOR_GRAY2BGRA);
		Utils.matToBitmap(threshMat, op);
		bmp = op;
		
		return bmp;
	}
	

	/**
	 * When new intent started with some specific information
	 * here we use this to handle the camera and gallery data
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case GALLERY_REQUEST:
				onButtonPressed(data.getData());

				break;
			case CAMERA_REQUEST:
				onButtonPressed(data.getData());
				break;
			}
		}
	}

	/**
	 * Initializing all our views
	 */
	private void initializer() {
		backView = (ImageView) findViewById(R.id.dImageView);
		frontView = (ImageView) findViewById(R.id.dImageViewFront);
		view = (RelativeLayout) findViewById(R.id.relativity);
	}

	/**
	 * Either Camera Button pressed or gallery
	 * In Both cases we need to get the path of
	 * the image and then save it in the
	 * variabel
	 * @param r
	 */
	private void onButtonPressed(Uri r) {
		Uri image = r;
		try {
			galleryBmp = Images.Media.getBitmap(getContentResolver(), image);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		onGetImage(galleryBmp);
	}
	
	
	/**
	 * After getting image from camera or gallery
	 * We have to do some calculation on which we
	 * rotate the screen or image
	 * @param bm
	 */
	private void onGetImage(Bitmap bm) {

		if (bm.getWidth() > bm.getHeight()) {
			Bitmap bMapRotate = null;
			Matrix mat = new Matrix();
			mat.postRotate(90);
			bMapRotate = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(),
					bm.getHeight(), mat, true);
			bm.recycle();
			bm = null;
			galleryBmp = Bitmap.createScaledBitmap(bMapRotate,
					backView.getWidth(), backView.getHeight(), true);
			backView.setImageBitmap(bMapRotate);
		} else {
			galleryBmp = Bitmap.createScaledBitmap(bm, backView.getWidth(),
					backView.getHeight(), true);
			backView.setImageBitmap(bm);
		}

		// AutoApplyCanny(galleryBmp);
	}

	/**
	 * A simple dialog holds color picker 
	 * User can select color from it
	 */
	protected void ColorPicker() {
		AmbilWarnaDialog dg = new AmbilWarnaDialog(this, Color.BLACK, true,
				new OnAmbilWarnaListener() {
					@Override
					public void onOk(AmbilWarnaDialog dialog, int colors) {
						replacementColor = colors;

					}

					@Override
					public void onCancel(AmbilWarnaDialog dialog) {
						Toast.makeText(getBaseContext(),
								"You did not choose any Color",
								Toast.LENGTH_SHORT).show();

					}
				});

		dg.show();
	}

	/**
	 * On Back Press we need to go back to the
	 *main activity where we are showing choose
	 * b/w modes
	 */
	@Override
	public void onBackPressed() {
		this.finish();
		startActivity(new Intent(this, MainActivity.class));

	}

	/**
	 * calling Camere
	 */
	private void callTehCamIntent() {
		Intent camInt = new Intent("android.media.action.IMAGE_CAPTURE");
		startActivityForResult(camInt, CAMERA_REQUEST);
	}

	/**
	 * calling Gallery
	 */
	private void callTheGalIntent() {
		Intent galleryIntent = new Intent();
		galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
		galleryIntent.setType("image/*");
		startActivityForResult(galleryIntent, GALLERY_REQUEST);

	}
}
