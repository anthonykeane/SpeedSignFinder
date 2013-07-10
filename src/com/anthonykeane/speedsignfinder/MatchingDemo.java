package com.anthonykeane.speedsignfinder;

/**
 * Created by anthony on 9/07/13.
 */

class MatchingDemo {
//    public void run(String inFile, String templateFile, String outFile, int match_method) {
//        System.out.println("\nRunning Template Matching");
//
//        Mat img = Highgui.imread(inFile);
//        URI uri = URI.create("android.resource://com.anthonykeane.speedsignfinder/raw/speedlimit55.jpg");
//
//
//        bitmap batt= bitmapFactory.decodeFile(uri);
//        Utils.bitmapToMat(batt, mBatt);
//        // battHeight and battWidth should have the height and width of your bitmap...
//        Mat mBatt = new Mat(battHeight, battWidth, CvType.CV_8UC1);
//
//
//
//
//
//
//        Mat templ =  R.raw.speedlimit55;
//
//
//        // / Create the result matrix
//        int result_cols = img.cols() - templ.cols() + 1;
//        int result_rows = img.rows() - templ.rows() + 1;
//        Mat result = new Mat(result_rows, result_cols, CvType.CV_32FC1);
//
//        // / Do the Matching and Normalize
//        Imgproc.matchTemplate(img, templ, result, match_method);
//        Core.normalize(result, result, 0, 1, Core.NORM_MINMAX, -1, new Mat());
//
//        // / Localizing the best match with minMaxLoc
//        MinMaxLocResult mmr = Core.minMaxLoc(result);
//
//        Point matchLoc;
//        if (match_method == Imgproc.TM_SQDIFF || match_method == Imgproc.TM_SQDIFF_NORMED) {
//            matchLoc = mmr.minLoc;
//        } else {
//            matchLoc = mmr.maxLoc;
//        }
//
//        // / Show me what you got
//        Core.rectangle(img, matchLoc, new Point(matchLoc.x + templ.cols(),
//                matchLoc.y + templ.rows()), new Scalar(0, 255, 0));
//
//        // Save the visualized detection.
//        System.out.println("Writing "+ outFile);
//        Highgui.imwrite(outFile, img);
//
//    }
}

