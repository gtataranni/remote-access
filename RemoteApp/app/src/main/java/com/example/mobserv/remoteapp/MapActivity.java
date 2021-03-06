package com.example.mobserv.remoteapp;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Created by pacellig on 13/12/2015.
 */
public class MapActivity extends Activity implements LocationListener, OnMapReadyCallback, GoogleMap.OnMapClickListener {

    Double lat, lon;
    MapFragment mapFragment;
    GoogleMap googleMap;
    String nametoshow = null;
    String toSendOrToShow=null;
    private int maptype = GoogleMap.MAP_TYPE_NORMAL;
    private static final int SEND_GPS = 111;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mapfragment);

        Intent it = getIntent();
        Bundle b = it.getExtras();
        toSendOrToShow = b.getString("sendOrShow");
        if ( toSendOrToShow.compareTo("showPosition")==0) {
            lat = b.getDouble("latitude");
            lon = b.getDouble("longitude");
            nametoshow = b.getString("nametoshow");
        } else if ( toSendOrToShow.compareTo("readPosition")==0 ){
            // code to be executed in case i want to click and send the position
            nametoshow = b.getString("nametoshow");
            lat = 43.614386; // default cordinates for the map
            lon = 7.071125;
        }
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onMapClick(LatLng latLng) {
        if ( toSendOrToShow.compareTo("readPosition")==0 ) {
            //Toast.makeText(this, String.valueOf(lat) + " " String.valueOf(lon), Toast.LENGTH_LONG).show();
            Intent it = new Intent();
            it.putExtra("latS", String.valueOf(latLng.latitude));
            it.putExtra("lonS", String.valueOf(latLng.longitude));
            it.putExtra("altS", String.valueOf(0)); // cannot get altitude
            setResult(Activity.RESULT_OK, it);
            finish();
        }
        else {
            // do nothing
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        LatLng position = new LatLng(lat,lon);

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(position)
                .zoom(17)
                .bearing(90)
                .tilt(30)
                .build();
        this.googleMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(nametoshow)
                        .snippet(String.valueOf(position.latitude)+","+String.valueOf(position.longitude))
        );
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.setOnMapClickListener(this);
    }

    public void onClickChangeMapType(View view){

        if ( maptype == GoogleMap.MAP_TYPE_HYBRID ) {
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            maptype = GoogleMap.MAP_TYPE_NORMAL;
        } else {
            googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            maptype = GoogleMap.MAP_TYPE_HYBRID;
        }
    }

}
