package com.rommel.steven.musea;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.bonuspack.overlays.Polyline;
import org.osmdroid.bonuspack.routing.MapQuestRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MainAct extends Activity {

    private MapView mapView;
    private String DBUrl = "https://stevencleys.cloudant.com/musea/locations";
    private String MQkey = "Fmjtd%7Cluurnua2n9%2Cbx%3Do5-9w8xhf"; //key voor route ophaling
    private LocationManager locationManager;
    private LocationListener locationListener;
    private RequestQueue mRequestQueue;
    private GeoPoint currentPos;
    private Marker tracker;
    private Polyline roadOverlay;
    private List<String> musea = new ArrayList<String>();
    private AutoCompleteTextView searchBox;
    private String routeType;
    private SharedPreferences sharedPref = null;
    private Boolean GPSOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPref = getPreferences(MODE_PRIVATE); //get route type prefs
        mapView = (MapView) findViewById(R.id.mapView); //map creation

        tracker = new Marker(mapView); //declare gps tracking marker
        tracker.hideInfoWindow();
        tracker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker, MapView mapView) {
                tracker.hideInfoWindow();  //disabling tooltip on the gps marker
                return false;
            }
        });
        tracker.setIcon(getResources().getDrawable(R.drawable.marker_poi_default));

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(getApplicationContext(), "GPS not enabled!", Toast.LENGTH_SHORT).show();
        } else {
            GPSOn = true;
            locationListener = new MyLocationListener();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);

        }

        mapView.setTileSource(TileSourceFactory.MAPQUESTOSM);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(16);

        mRequestQueue = Volley.newRequestQueue(this);

        JsonObjectRequest jr = new JsonObjectRequest(Request.Method.GET, DBUrl, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) { //get database
                try {
                    JSONArray arr = response.getJSONArray("museumoverzicht");

                    for (int i = 0; i < arr.length(); i++) {
                        final double lng = Double.parseDouble(arr.getJSONObject(i).getString("point_lng"));
                        final double lat = Double.parseDouble(arr.getJSONObject(i).getString("point_lat"));
                        final String title = arr.getJSONObject(i).getString("naam");
                        final String straat = arr.getJSONObject(i).getString("straat");
                        final String huisnr = arr.getJSONObject(i).getString("huisnummer");

                        Marker marker = new Marker(mapView);
                        marker.hideInfoWindow();
                        musea.add(title);
                        marker.setTitle(title);
                        marker.setSubDescription(straat + " " + huisnr);
                        marker.setPosition(new GeoPoint(lat, lng));
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                            @Override
                            public boolean onMarkerClick(Marker marker, MapView mapView) {
                                searchBox.setText(title + " ");
                                marker.showInfoWindow();
                                mapView.getController().animateTo(new GeoPoint(lat, lng)); //center to marker

                                if (GPSOn)  //dont execute without gps enabled
                                    new Routes().execute(new GeoPoint(lat, lng));

                                return false;
                            }
                        });
                        mapView.getOverlays().add(marker);
                    }
                    Collections.sort(musea); //alfabetisch sorteren

                    if (!GPSOn) {
                        currentPos = new GeoPoint(51.21968667200008, 4.4009229560000449); //go to here if gps is disabled
                        mapView.getController().animateTo(currentPos);
                    }
                    else {
                        Location lastLoc = locationManager.getLastKnownLocation(locationManager.GPS_PROVIDER); //haal laatste locatatie op die bekend was (locationonchange word soms niet direct uitgevoerd)
                        if(lastLoc != null) {
                            currentPos = new GeoPoint(lastLoc.getLatitude(), lastLoc.getLongitude());
                            Log.e("last known loc", currentPos.toString());
                            tracker.setPosition(currentPos);
                            mapView.getController().setCenter(currentPos);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("com.rommel.steven.musea", error.getMessage());
            }
        });

        mRequestQueue.add(jr);

        searchBox = (AutoCompleteTextView) findViewById(R.id.searchCTV); //searchbox
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line, musea);
        searchBox.setAdapter(adapter);
        searchBox.setThreshold(1); //default is 2
        searchBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchBox.showDropDown();
            }
        });
        searchBox.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.e("hurrrr", (String) parent.getItemAtPosition(position));
                for (Object item : mapView.getOverlays()) {
                    if (item instanceof Marker) { //check if its a marker
                        if (((Marker) item).getTitle() == (String) parent.getItemAtPosition(position)) { //search for selected marker
                            hideSoftKeyBoard();
                            GeoPoint target = ((Marker) item).getPosition();  //moved to selected museum
                            mapView.getController().animateTo(target);
                            ((Marker) item).showInfoWindow();
                            if (GPSOn) {
                                new Routes().execute(target);
                            }
                        }
                    }
                }
            }
        });


        tracker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(tracker);
        mapView.invalidate();
    }

    private void hideSoftKeyBoard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm.isAcceptingText()) { // verify if the soft keyboard is open
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        routeType = sharedPref.getString(getString(R.string.route), getString(R.string.ped));

        if (routeType.equals(getString(R.string.car)))
            menu.findItem(R.id.action_Auto).setChecked(true);
        else if (routeType.equals(getString(R.string.bicycle)))
            menu.findItem(R.id.action_Fiets).setChecked(true);
        else
            menu.findItem(R.id.action_Voet).setChecked(true);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences.Editor editor = sharedPref.edit();
        switch (item.getItemId()) {
            case R.id.action_Fiets:
                if (item.isChecked()) item.setChecked(false);
                else item.setChecked(true);
                editor.putString(getString(R.string.route), getString(R.string.bicycle)).apply();
                return true;
            case R.id.action_Voet:
                if (item.isChecked()) item.setChecked(false);
                else item.setChecked(true);
                editor.putString(getString(R.string.route), getString(R.string.ped)).apply();
                return true;
            case R.id.action_Auto:
                if (item.isChecked()) item.setChecked(false);
                else item.setChecked(true);
                editor.putString(getString(R.string.route), getString(R.string.car)).apply();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class Routes extends AsyncTask<GeoPoint, Void, Void> { //async method om route bepaling te doen (mag niet in main thread)

        @Override
        protected Void doInBackground(GeoPoint... params) {
            GeoPoint target = new GeoPoint(params[0]);
            RoadManager roadManager = new MapQuestRoadManager(MQkey);
            routeType = sharedPref.getString(getString(R.string.route), getString(R.string.ped)); //if no sharedpref is available, pedestrian will be default
            roadManager.addRequestOption("routeType=" + routeType); //route type
            ArrayList<GeoPoint> waypoints = new ArrayList<GeoPoint>();
            waypoints.add(currentPos);
            waypoints.add(target);
            final Road road = roadManager.getRoad(waypoints); //calculate route between current and target pos
            runOnUiThread(new Runnable() { //Android “Only the original thread that created a view hierarchy can touch its views.” error
                @Override
                public void run() {
                    if (roadOverlay != null) { //remove old route if present
                        mapView.getOverlays().remove(roadOverlay);
                        mapView.invalidate();
                    }

                    roadOverlay = RoadManager.buildRoadOverlay(road, mapView.getContext());
                    mapView.getOverlays().add(roadOverlay);

                    mapView.invalidate();
                }
            });
            return null;
        }
    }

    private class MyLocationListener implements LocationListener {
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
            GPSOn = true;
        }

        @Override
        public void onProviderDisabled(String provider) {
            GPSOn = false;
        }

        @Override
        public void onLocationChanged(Location loc) {
            currentPos = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            mapView.getController().animateTo(currentPos);
            Log.e("current", currentPos.toString());
            tracker.setPosition(currentPos);
            mapView.invalidate();
        }
    }
}
