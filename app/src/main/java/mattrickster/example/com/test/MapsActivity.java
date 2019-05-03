package mattrickster.example.com.test;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    FloatingActionButton myLocation,add;
    SearchView searchView;

    ArrayList markerPoints= new ArrayList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        myLocation = findViewById(R.id.my_location);
        add = findViewById(R.id.add);

        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MapsActivity.this,BottomDrawerActivity.class));

            }
        });

        myLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Location location = mMap.getMyLocation();
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 20.0f));
            }
        });

        searchView = findViewById(R.id.search);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {

                Geocoder geoCoder = new Geocoder(MapsActivity.this, Locale.getDefault());

                List<Address> addresses;

                Location location = mMap.getMyLocation();

                try{
                    addresses = geoCoder.getFromLocationName(s,1);
                    if(addresses.size() > 0){

                        Double lat = (addresses.get(0).getLatitude());
                        Double lon = (addresses.get(0).getLongitude());

                        MarkerOptions markerOptions = new MarkerOptions();
                        markerOptions.position(new LatLng(lat,lon));

                        mMap.clear();
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat,lon),10.0f));
                        mMap.addMarker(markerOptions);

                        LatLng orig = new LatLng(location.getLatitude(),location.getLatitude());
                        LatLng dest = new LatLng(lat,lon);

                        String url = getDirectionsUrl(orig, dest);

                        DownloadTask downloadTask = new DownloadTask();
                        downloadTask.execute(url);

                }else{

                        AlertDialog.Builder adb = new AlertDialog.Builder(MapsActivity.this);
                        adb.setTitle("Google Map");
                        adb.setMessage("Please Provide the Proper Place");
                        adb.setPositiveButton("Close",null);
                        adb.show();

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;


        if (ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission
                (MapsActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        } else {

            googleMap.setMyLocationEnabled(true);
        }

    }

    private class DownloadTask extends AsyncTask<String,String,String> {

        @Override
        protected String doInBackground(String... url) {

            String data = "";

            try {
                data = downloadUrl(url[0]);
                Log.i("TAG",""+data);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();
            parserTask.execute(result);

        }
    }

    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String,String>>>> {

        @Override
        protected List<List<HashMap<String,String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String,String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String,String>>> result) {
            ArrayList points = new ArrayList<LatLng>();
            PolylineOptions lineOptions = new PolylineOptions();
            lineOptions.width(2);
            lineOptions.color(Color.RED);
            MarkerOptions markerOptions = new MarkerOptions();

            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList();
                lineOptions = new PolylineOptions();

                List<HashMap<String,String>> path = result.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat").toString());
                    double lng = Double.parseDouble(point.get("lng").toString());
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                lineOptions.addAll(points);
                lineOptions.width(12);
                lineOptions.color(Color.RED);
                lineOptions.geodesic(true);

            }

            if (points.size() != 0)
                mMap.addPolyline(lineOptions);
        }
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {

        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        String sensor = "sensor=false";
        String mode = "mode=driving";

        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode;

        String output = "json";

        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=AIzaSyC67qfyxJUZHzJFgx2wFg5qZ_3cqRsNJ9w";

        Log.i("TAG",""+url);

        return url;
    }

    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.connect();

            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }



}
