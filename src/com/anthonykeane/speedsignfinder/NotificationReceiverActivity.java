package com.anthonykeane.speedsignfinder;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import java.io.*;
import java.util.ArrayList;

public class NotificationReceiverActivity extends Activity {

    private static final int intentSendEmail = 2;
    public String myInternalFile = "ToBeEmailed";// used to R/W internal file.
    private String userEmail = "";


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to

        switch (requestCode) {
            case intentSendEmail:
                // The Intent's data Uri identifies which contact was selected.
                File myDirectoryPath = getFilesDir();
                File dir = new File(String.valueOf(myDirectoryPath));
                for (File fileIn : dir.listFiles()) {
                    if (fileIn.getName().endsWith(".png")) {
                        fileIn.delete();
                    }
                }
                super.finish();
                break;

        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notificationclicked);

        PackageInfo pinfo = null;
        try {
            pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        SharedPreferences appSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        userEmail = appSharedPrefs.getString(getString(R.string.settings_userEmailKey), "");


        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("plain/text");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.emailAddress)});
        intent.putExtra(Intent.EXTRA_BCC, new String[]{userEmail});
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emailSubject).concat(" ").concat(pinfo != null ? pinfo.versionName : null));
        intent.putExtra(Intent.EXTRA_TEXT, pakReadInternal());


        File myDirectoryPath = getFilesDir();
        File dir = new File(String.valueOf(myDirectoryPath));
        ArrayList<Uri> uris = new ArrayList<Uri>();
        //convert from paths to Android friendly Parcelable Uri's
        for (File fileIn : dir.listFiles()) {
            if (fileIn.getName().endsWith(".png")) {

                Uri u = Uri.fromFile(fileIn);
                uris.add(u);
            }
        }

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);


//
//                                    File myDirectoryPath = getFilesDir();
//                                   // File[] files = myDirectoryPath.listFiles();
//
//                                    int x = 0;
//                                    File dir = new File(String.valueOf(myDirectoryPath));
//                                    for (File child : dir.listFiles()) {
//                                        // Do something with child
//
//                                        if (child.getName().endsWith(".png"))
//                                        {
//
//                                            // TODO add images to email.
//                                            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(child.toString()));
//
//


        // dropping this (below)
        //                x++;
        //
        //
        //                //to retrieve image using id set in xml.
        //                String imageId = "imageId" + String.valueOf(x);
        //                int resID;
        //                resID = getResources().getIdentifier(imageId ,"id","com.anthonykeane.speedsignfinder");
        //                ImageView image = (ImageView) findViewById(resID);
        //
        //
        //
        //
        //
        //
        //                ImageView MyImageView = (ImageView)findViewById(x);
        //                //MyImageView.setMinimumHeight(100);
        //                Drawable d = Drawable.createFromPath( child.toString());
        //                MyImageView.setImageDrawable(d);
//                                        }
//                                    }


        pakStartInternalFile();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

        startActivityForResult(intent, intentSendEmail);

    }


    private void pakStartInternalFile() {
        FileOutputStream fos;
        try {
            // Note OVER WRIGTH                         ----
            fos = openFileOutput(myInternalFile, MODE_PRIVATE);
//			fos.write("\n\r".getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String pakReadInternal() {
        FileInputStream fis;

        String myData = getString(R.string.wwwHead);

        try {
            fis = openFileInput(myInternalFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return "No Data";
        }
        try {
            StringBuffer fileContent = new StringBuffer("");
            byte[] buffer = new byte[1024];
            while (fis.read(buffer) != -1) {
                fileContent.append(new String(buffer));
            }
            myData = myData + fileContent;
            fis.close();
        } catch (IOException e) {
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
