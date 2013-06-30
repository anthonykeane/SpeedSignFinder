package com.anthonykeane.speedsignfinder;

import org.opencv.core.Point;

/**
 * Created by anthony on 24/06/13.
 */
public class pakPoint extends Point {

    public double x, y;

    public pakPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    ///Added By PAK
    public Point offset(double offx, double offy) {
        return new Point(x + offx, y + offy);
    }


}
