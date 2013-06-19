package com.anthonykeane.speedsignfinder;

/**
 * Created by Keanea on 2/06/13.
 */
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

public class LocListener implements LocationListener {
	private static double lat = 0.0;
	private static double lon = 0.0;
	private static float bearing = 0;

	private static double alt = 0.0;
	private static float speed = 0;
	private static float accuracy = 0;


	public static double getLat() {
		return lat;
	}

	public static double getLon() {
		return lon;
	}


	public static double getAlt() {
		return alt;
	}


	public static double getSpeed() {
		return speed;
	}

	public static double getBearing() {
		return bearing;
	}


	public static double getBearing_45() {
		//deal with the 360dec thing
		if(bearing >= 45.0) return bearing - 45;
		else return ((bearing + 360) - 45);
	}


	@Override
	public void onLocationChanged(Location location) {
		lat = location.getLatitude();
		lon = location.getLongitude();
		alt = location.getAltitude();
		speed = location.getSpeed();
		bearing = location.getBearing();
		accuracy = location.getAccuracy();
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}
}
