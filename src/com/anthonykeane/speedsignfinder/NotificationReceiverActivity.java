package com.anthonykeane.speedsignfinder;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class NotificationReceiverActivity extends Activity {

	public String myInternalFile = "ToBeEmailed";// used to R/W internal file.


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.notificationclicked);

		PackageInfo pinfo = null;
		try {
			pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
		} catch(PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/html");
		intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.emailAddress)});
		intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emailSubject).concat(" ").concat(pinfo != null ? pinfo.versionName : null));
		intent.putExtra(Intent.EXTRA_TEXT, pakReadInternal());
		startActivity(intent);

		pakStartInternalFile();
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancelAll();

		super.finish();


	}


	private void pakStartInternalFile() {
		FileOutputStream fos;
		try {
			// Note OVER WRIGTH                         ----
			fos = openFileOutput(myInternalFile, MODE_PRIVATE);
//			fos.write("\n\r".getBytes());
			fos.close();
		} catch(IOException e) {
			e.printStackTrace();
		}

	}

	private String pakReadInternal() {
		FileInputStream fis;

		String myData = getString(R.string.wwwHead);

		try {
			fis = openFileInput(myInternalFile);
		} catch(FileNotFoundException e) {
			e.printStackTrace();
			return "No Data";
		}
		try {
			StringBuffer fileContent = new StringBuffer("");
			byte[] buffer = new byte[1024];
			while(fis.read(buffer) != -1) {
				fileContent.append(new String(buffer));
			}
			myData = myData + fileContent;
			fis.close();
		} catch(IOException e) {
			e.printStackTrace();
			return "No Data";
		}

		myData = myData.concat(getString(R.string.wwwTail));
		return myData;

	}

	@Override
	public void finish() {
		super.finish();
	}
}
