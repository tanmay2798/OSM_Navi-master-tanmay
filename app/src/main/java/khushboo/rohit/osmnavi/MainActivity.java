package khushboo.rohit.osmnavi;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.ObjectUtils;
import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.location.POI;
import org.osmdroid.bonuspack.routing.MapQuestRoadManager;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.location.OverpassAPIProvider;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.bonuspack.utils.HttpConnection;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;


public class MainActivity extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final int REQ_CODE_SPEECH_INPUT = 3;
    private static final String OSM_EDITING_URL = "http://api.openstreetmap.org/api/0.6//map?bbox=";
    MyItemizedOverlay myItemizedOverlay = null;
    SQLiteDatabase db;
    private MediaRecorder myAudioRecorder;
    private String outputFile = null;
    boolean isNavigating = false;
    long time_diff = 60 * 1000;
    Handler h = new Handler();
    int delay = 10; //milliseconds
    int osmNumInstructions, osmNextInstruction, prefetch_nextInstruction, Instruction_progressTracker;
    Button start, stop;
    Button button, save_button;
    String navigatingDistance;
    TextToSpeech tts;
    int prev_id = 0;
    ArrayList<GeoPoint> landmarks;
    ArrayList<String> instructions;
    ArrayList<Long> timestamps;
    OSRMRoadManager roadManager;
    Location mLastLocation;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    PlaceAutocompleteFragment endingDestination;
    Place destinationLatLng;
    boolean isSelected = false;
    GPSTracker gps;
    MyApp app;
    double current_lat, current_long;
    GeoPoint previous_location;
    private String TAG = MainActivity.class.getSimpleName();
    ////////////
    private static final int SPEECH_REQUEST_CODE = 0;
    private ProgressBar mProgressBar;
    private TextView mLoadingText;


    // added by sac
    OverpassAPIProvider overpassProvider;
    Map<Long, String> tagdescriptions;
    ArrayList<Long> tst; // timestamp for pre scheduling of future instructions, filling the empty spaces

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // System.out.println("onCreate function called");
        super.onCreate(savedInstanceState);

        if (PackageManager.PERMISSION_GRANTED !=
                checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1339);
        }
        if (PackageManager.PERMISSION_GRANTED !=
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1340);
        }
        if (PackageManager.PERMISSION_GRANTED !=
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1341);
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);//"android.intent.action.MEDIA_BUTTON"
        MediaButtonIntentReceiver r = new MediaButtonIntentReceiver();
        filter.setPriority(10000); //this line sets receiver priority
        registerReceiver(r, filter);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        landmarks = new ArrayList<GeoPoint>();
        instructions = new ArrayList<String>();
        timestamps = new ArrayList<Long>();
        tst = new ArrayList<>();
//        MapView map = (MapView) findViewById(R.id.map);
//        map.setTileSource(TileSourceFactory.MAPNIK);
//        map.setBuiltInZoomControls(true);
//        map.setMultiTouchControls(true);

//        GeoPoint startPoint = new GeoPoint(28.544837,77.194259);
//        IMapController mapController = map.getController();
//        mapController.setZoom(9);
//        mapController.setCenter(startPoint);
        roadManager = new OSRMRoadManager(this);
//        ArrayList<GeoPoint> waypoints = new ArrayList<GeoPoint>();
//        waypoints.add(startPoint);
//        GeoPoint endPoint = new GeoPoint(28.545213,77.192219);
//        waypoints.add(endPoint);
//        roadManager.addRequestOption("routeType=pedestrian");
//        Road road = roadManager.getRoad(waypoints);
//        Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
//        map.getOverlays().add(roadOverlay);
//        for (int i=0; i<road.mNodes.size(); i++){
//            System.out.println(road.mNodes.get(i).mLocation.getLatitude() + ", " + road.mNodes.get(i).mLocation.getLongitude());
//            System.out.println(road.mNodes.get(i).mInstructions);
//        }
//        map.invalidate();

        setContentView(R.layout.activity_main);
        button = (Button) findViewById(R.id.button8);
        save_button = (Button) findViewById(R.id.button_save);
        tts = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                // TODO Auto-generated method stub
                if (status == TextToSpeech.SUCCESS) {
//                    int result=tts.setLanguage(Locale.US);
//                    if(result==TextToSpeech.LANG_MISSING_DATA ||
//                            result==TextToSpeech.LANG_NOT_SUPPORTED){
//                        Log.e("error", "This Language is not supported");
//                    }
                    if (!app.hasRefreshed) {
                        System.out.println("Starting app");
                        tts.speak("Welcome to OSM Navi. Enter any destination and press top button to start navigating. Use the second button to save a landmark at the current location.", TextToSpeech.QUEUE_FLUSH, null);
                        app.hasRefreshed = true;
                    }
//                    else{
//                        ConvertTextToSpeech();
//                    }
                } else
                    Log.e("error", "Initialization Failed!");
            }
        });

//        gps = new GPSTracker(this);

        buildGoogleApiClient();
        app = (MyApp) this.getApplicationContext();
        db = app.myDb;
        db.execSQL("CREATE TABLE IF NOT EXISTS myLocation(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, lat INT,long INT,description VARCHAR, timestamp INT, prev_id INT, next_id INT );");
        db.execSQL("CREATE TABLE IF NOT EXISTS myTags(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tag VARCHAR );");
        db.execSQL("CREATE TABLE IF NOT EXISTS locationByTag(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tag_id INTEGER, location_id INTEGER);");
        db.execSQL("CREATE TABLE IF NOT EXISTS trackData(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, lat INT, long INT, type INT, timestamp INT );");
        db.execSQL("CREATE TABLE IF NOT EXISTS routes(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR, distance VARCHAR);");
        db.execSQL("CREATE TABLE IF NOT EXISTS routebyinstructions(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, routeid INTEGER, lat INT, long INT, description VARCHAR);");
        endingDestination = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.ending_destination_2);
        LatLng southWestBound = new LatLng(7.597576, 67.345201);
        LatLng northEastBound = new LatLng(38.733380, 96.964342);
        LatLngBounds indiaBounds = new LatLngBounds(southWestBound, northEastBound);
        endingDestination.setBoundsBias(indiaBounds);
        endingDestination.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                isSelected = true;
                // TODO: Get info about the selected place.
                destinationLatLng = place;
                Log.i("endingDestination ", "Place: " + place.getName());
                tts.speak("Entered destination is : " + place.getName() + ". Press top button to start navigating.", TextToSpeech.QUEUE_FLUSH, null);
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                Log.i("endingDestination ", "An error occurred: " + status);
                tts.speak("There was an error setting up the destination. Please try again.", TextToSpeech.QUEUE_FLUSH, null);
            }
        });

        h.postDelayed(new Runnable() {
            public void run() {
                getLocalInfo();
                h.postDelayed(this, delay);
            }
        }, delay);


    }

    public void promptSpeechInputView(View view) {
        // System.out.println("promptSpeechInputView function called");
        promptSpeechInput();
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }


    public void changeLayout(View view) {
        Intent i = new Intent(getBaseContext(), AddButton.class);
        startActivityForResult(i, 1);
    }

    synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();


    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tts.shutdown();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // System.out.println("onActivityResult function called");
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                String myDescription = data.getStringExtra("result");
                boolean[] tags = data.getBooleanArrayExtra("tags");
                double lat_float = current_lat;
                double long_float = current_long;
                int lat_int = (int) (lat_float * 10000000);
                int long_int = (int) (long_float * 1000000);


                int new_row_id;
                if (prev_id > 0) {
                    Cursor c = db.rawQuery("SELECT * from myLocation where id = '" + prev_id + "'", null);
                    c.moveToFirst();
                    int next_id;
                    next_id = Integer.parseInt(c.getString(6)); // The next id of prev, if present
                    c.close();

                    // update the next id of new point with this, and update the next id of previous one. If next id was 0, it will remain 0
                    db.execSQL("INSERT INTO myLocation VALUES(NULL, '" + lat_int + "','" +
                            long_int + "','" + myDescription + "','" + System.currentTimeMillis() +
                            "','" + prev_id + "','" + next_id + "');");
                    Cursor c_new = db.rawQuery("SELECT last_insert_rowid()", null);
                    c_new.moveToFirst();
                    new_row_id = Integer.parseInt(c_new.getString(0));
                    c_new.close();
                    db.execSQL("UPDATE myLocation SET next_id = '" + new_row_id + "' WHERE id = '" + prev_id + "'");
                } else {
                    db.execSQL("INSERT INTO myLocation VALUES(NULL, '" + lat_int + "','" +
                            long_int + "','" + myDescription + "','" + System.currentTimeMillis() +
                            "','" + prev_id + "','0');");
                    Cursor c_new = db.rawQuery("SELECT last_insert_rowid()", null);
                    c_new.moveToFirst();
                    new_row_id = Integer.parseInt(c_new.getString(0));
                    c_new.close();
                }
                prev_id = new_row_id;
                for (int i = 0; i < tags.length; i++) {
                    if (tags[i]) {
                        db.execSQL("INSERT INTO locationByTag VALUES(NULL, " + (i + 1) + ", " + prev_id + ");");
                    }
                }
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                //Write your code if there's no result
            }
        } else if (requestCode == 2) {                               // request from
            if (resultCode == RESULT_OK) {
                String routeName = data.getStringExtra("name");
                db.execSQL("INSERT INTO routes VALUES(NULL, '" + routeName + "', '" + navigatingDistance + "');");
                Cursor c_new = db.rawQuery("SELECT last_insert_rowid()", null);
                c_new.moveToFirst();
                int new_row_id = Integer.parseInt(c_new.getString(0));
                c_new.close();
                for (int i = 0; i < instructions.size(); i++) {
                    String instruction = instructions.get(i);
                    double lat_float = landmarks.get(i).getLatitude();
                    int lat_int = (int) (lat_float * 10000000);
                    double long_float = landmarks.get(i).getLatitude();
                    int long_int = (int) (long_float * 10000000);
                    db.execSQL("INSERT INTO routebyinstructions VALUES(NULL, '" + new_row_id + "','" +
                            lat_int + "','" + long_int + "','" + instruction + "');");
                }
            }
        } else if (requestCode == REQ_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && null != data) {

                ArrayList<String> result = data
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                endingDestination.setText(result.get(0));
            }
        } else if (requestCode == 4) {
            if (resultCode == RESULT_OK) {
                int selectedRoute = data.getIntExtra("selectedRoute", 0);
                navigatingDistance = data.getStringExtra("selectedRouteDistance");
                Cursor c = db.rawQuery("SELECT * FROM routebyinstructions where routeid = " + selectedRoute + ";", null);
                double max_latitude = current_lat;
                double min_latitude = current_lat;
                double max_longitude = current_long;
                double min_longitude = current_long;
                while (c.moveToNext()) {
                    double latitude = Double.parseDouble(c.getString(2)) / 10000000;
                    double longitude = Double.parseDouble(c.getString(3)) / 10000000;
                    String description = c.getString(4);
                    landmarks.add(new GeoPoint(latitude, longitude));
                    instructions.add(description);
                    timestamps.add(new Long(0));
                    tst.add(new Long(0));
                    if (latitude > max_latitude) {
                        max_latitude = latitude;
                    }
                    if (latitude < min_latitude) {
                        min_latitude = latitude;
                    }
                    if (longitude > max_longitude) {
                        max_longitude = longitude;
                    }
                    if (longitude < min_longitude) {
                        min_longitude = longitude;
                    }
                }
//                int max_latitude_int = (int) (max_latitude * 10000000);
//                int max_longitude_int = (int) (max_longitude * 10000000);
//                int min_latitude_int = (int) (min_latitude * 10000000);
//                int min_longitude_int = (int) (min_longitude * 10000000);
//                Cursor c2 = db.rawQuery("SELECT * FROM myLocation WHERE lat BETWEEN " + (min_latitude_int) + " AND " + (max_latitude_int) + " AND long BETWEEN " + (min_longitude_int) + " AND " + (max_longitude_int), null);
//                while (c2.moveToNext()) {
//                    double latitude = Double.parseDouble(c2.getString(1)) / 10000000;
//                    double longitude = Double.parseDouble(c2.getString(2)) / 10000000;
//                    String description = c2.getString(3);
//                    landmarks.add(new GeoPoint(latitude, longitude));
//                    instructions.add(description);
//                    timestamps.add(new Long(0));
//                }
                isNavigating = true;
                tts.speak("Starting Navigation. Your location is " + navigatingDistance + " away.", TextToSpeech.QUEUE_FLUSH, null);
                button.setText("Stop");
                save_button.setText("Save this route");
            }
        }
        ///////////////////////////////////////////////////
        else if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null);
            Log.i(TAG, spokenText);
            TextView edt = (TextView) findViewById(R.id.edt);
            edt.setText(spokenText);

            if (spokenText.equalsIgnoreCase("start")) {
                Button one = (Button) findViewById(R.id.button8);
                one.performClick();
            }
            if (spokenText.equalsIgnoreCase("next instruction")) {
                Button one = (Button) findViewById(R.id.buttonNext);
                one.performClick();
            }
            if (spokenText.equalsIgnoreCase("add text")) {
                Button one = (Button) findViewById(R.id.button);
                one.performClick();
            }
            if (spokenText.equalsIgnoreCase("saved routes")) {
                Button one = (Button) findViewById(R.id.button_save);
                one.performClick();
            }
            if (spokenText.equalsIgnoreCase("around me")) {
                Button one = (Button) findViewById(R.id.buttonAM);
                one.performClick();
            }
            if (spokenText.equalsIgnoreCase("export dp")) {
                Button one = (Button) findViewById(R.id.button5);
                one.performClick();
            }
            if (spokenText.equalsIgnoreCase("debug")) {
                Button one = (Button) findViewById(R.id.button9);
                one.performClick();
            }
            if (spokenText.equalsIgnoreCase("clear data")) {
                Button one = (Button) findViewById(R.id.button10);
                one.performClick();
            }


        }
    }

    public void setAudio(View view) {
        System.out.println(Environment.getExternalStorageDirectory().getAbsolutePath());
        outputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recording.3gp";
        ;

        myAudioRecorder = new MediaRecorder();
        myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        myAudioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);

        myAudioRecorder.setOutputFile(outputFile);

        setContentView(R.layout.record_view);
        start = (Button) findViewById(R.id.button6);
        stop = (Button) findViewById(R.id.button7);
        start.setEnabled(true);
        stop.setEnabled(false);

    }

    public void start_audio(View view) {
        try {
            myAudioRecorder.prepare();
            myAudioRecorder.start();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (java.io.IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        stop.setEnabled(true);
        start.setEnabled(false);
        Toast.makeText(getApplicationContext(), "Recording started", Toast.LENGTH_LONG).show();
    }

    public void stop_audio(View view) {
        myAudioRecorder.stop();
        myAudioRecorder.release();
        myAudioRecorder = null;

        stop.setEnabled(false);
        start.setEnabled(false);

        Toast.makeText(getApplicationContext(), "Audio recorded successfully", Toast.LENGTH_LONG).show();


    }

    public void onSaveButton(View view) {
        // System.out.println("onSaveButton function called");
        if (isNavigating) {
            Intent i = new Intent(getBaseContext(), SaveRoute.class);
            startActivityForResult(i, 2);
        } else {
            Intent i = new Intent(getBaseContext(), ShowRoutes.class);
            ArrayList<Integer> route_ids;
            route_ids = new ArrayList<Integer>();
            ArrayList<String> route_names;
            route_names = new ArrayList<String>();
            ArrayList<String> route_distances;
            route_distances = new ArrayList<String>();
            Cursor c = db.rawQuery("SELECT * FROM routes;", null);
            while (c.moveToNext()) {
                route_ids.add(Integer.parseInt(c.getString(0)));
                route_names.add(c.getString(1));
                route_distances.add(c.getString(2));
            }
            i.putExtra("route_ids", route_ids);
            i.putExtra("route_names", route_names);
            i.putExtra("route_distances", route_distances);
            startActivityForResult(i, 4);
        }
    }

    public void onStartButton(View view) {
        // System.out.println("onStartButton function called");
        if (!isNavigating) {
//            EditText edit =  (EditText) findViewById(R.id.editText2);
//            String destination = edit.getText().toString();
            if (!isSelected) {
                tts.speak("Destination not set", TextToSpeech.QUEUE_FLUSH, null);
                Toast.makeText(getApplicationContext(), "Destination not set!", Toast.LENGTH_LONG).show();
                return;
            } else {
                tts.speak("Please wait while we search for a suitable route.", TextToSpeech.QUEUE_FLUSH, null);
            }
            GeoPoint startPoint = new GeoPoint(current_lat, current_long);
            previous_location = startPoint;
            //            GeoPoint endPoint = new GeoPoint(Double.parseDouble(destination.split(",")[0]), Double.parseDouble(destination.split(",")[1]));
            GeoPoint endPoint = new GeoPoint(destinationLatLng.getLatLng().latitude, destinationLatLng.getLatLng().longitude);
            ArrayList<GeoPoint> waypoints = new ArrayList<GeoPoint>();
            waypoints.add(startPoint);
            waypoints.add(endPoint);
//            roadManager.addRequestOption("routeType=pedestrian");
            roadManager.setService("http://router.project-osrm.org/route/v1/pedestrian/");
            // roadManager.addRequestOption("alternatives=3");
            double max_latitude = current_lat;
            double min_latitude = current_lat;
            double max_longitude = current_long;
            double min_longitude = current_long;
            System.out.println("Starting point is: " + startPoint.toDoubleString());

            // original line earlier
            Road road = roadManager.getRoad(waypoints);
            // System.out.println("Number of roads found: " + road.length);

            // added check
            boolean roadstatusflag = true;
            if (road.mStatus != Road.STATUS_OK) {
                System.out.println("~~~~~~~THERE WAS ISSUE WITH THE STATUS OF THE ROADS~~~~~~~~");
                Toast.makeText(getApplicationContext(), "Network error!", Toast.LENGTH_LONG).show();
                tts.speak("Network Issue. Please try again!", TextToSpeech.QUEUE_FLUSH, null);
                roadstatusflag = false;
            }
            overpassProvider = new OverpassAPIProvider();
            for (int i = 1; i < road.mNodes.size(); i++) {
                if (road.mNodes.get(i).mInstructions != null && !road.mNodes.get(i).mInstructions.isEmpty()) {
                    GeoPoint loc = road.mNodes.get(i).mLocation;

                    // added by sac
                    BoundingBox bb = new BoundingBox(loc.getLatitude() + 0.000001, loc.getLongitude() + 0.000001, loc.getLatitude() - 0.000001, loc.getLongitude() - 0.000001);

                    // url for highway type
                    String urlforpoirequest = overpassProvider.urlForPOISearch("\"highway\"", bb, 10, 25);
                    ArrayList<POI> points = overpassProvider.getPOIsFromUrl(urlforpoirequest);
                    if (points == null) System.out.println("overpass returning nothing");
                    if (points != null) System.out.println("Size is: " + points.size());
                    int ptr = 0;
                    while (points != null && ptr < points.size()) {
                        String typeofhighway = points.get(ptr).mDescription;
                        if (typeofhighway.trim().matches("traffic_signals") || typeofhighway.trim().matches("motorway_junction")) {
                            landmarks.add(points.get(ptr).mLocation);
                            System.out.println("Added: " + points.get(ptr).mLocation + " " + typeofhighway);
                            instructions.add("Presence of " + typeofhighway);
                            //tts.speak(typeofhighway + "highway at " + points.get(ptr).mLocation, TextToSpeech.QUEUE_FLUSH, null);

                        }
                        // tagdescriptions.put(points.get(0).mId, loc.getLatitude()+","+loc.getLongitude()+":"+points.get(0).mDescription);
                        // System.out.println("Tags for: "+points.get(ptr).mId+" -> "+points.get(ptr).mCategory+" "+points.get(ptr).mType+" "+points.get(ptr).mLocation.toDoubleString()+" "+points.get(ptr).mDescription);
                        ptr++;
                    }

                    // url for lanes
                    System.out.println("Starting to find the number of lanes");
                    String urlforpoirequest2 = overpassProvider.urlForPOISearch("\"lanes\"", bb, 10, 25);
                    ArrayList<POI> points2 = overpassProvider.getPOIsFromUrl(urlforpoirequest2);
                    if (points2 == null) System.out.println("overpass returning nothing");
                    if (points2 != null) System.out.println("Size is: " + points2.size());
                    ptr = 0;
                    while (points2 != null && ptr < points2.size()) {
                        String lanes = points2.get(ptr).mDescription;
                        if (lanes != null) {
                            // landmarks.add(points.get(ptr).mLocation);
                            System.out.println("For lanes Added: " + points2.get(ptr).mLocation + " " + lanes + " " + points2.get(ptr).mType);
                            // instructions.add("Presence of "+typeofhighway);
                            //tts.speak(lanes + "lanes at " + points.get(ptr).mLocation, TextToSpeech.QUEUE_FLUSH, null);

                        }
                        // tagdescriptions.put(points.get(0).mId, loc.getLatitude()+","+loc.getLongitude()+":"+points.get(0).mDescription);
                        // System.out.println("Tags for: "+points.get(ptr).mId+" -> "+points.get(ptr).mCategory+" "+points.get(ptr).mType+" "+points.get(ptr).mLocation.toDoubleString()+" "+points.get(ptr).mDescription);
                        ptr++;
                    }

                    // url for surface type
                    System.out.println("Starting to find the type of surface");
                    String urlforpoirequest3 = overpassProvider.urlForPOISearch("\"surface\"", bb, 10, 25);
                    ArrayList<POI> points3 = overpassProvider.getPOIsFromUrl(urlforpoirequest3);
                    if (points3 == null) System.out.println("overpass returning nothing");
                    if (points3 != null) System.out.println("Size is: " + points3.size());
                    ptr = 0;
                    while (points3 != null && ptr < points3.size()) {
                        String surf = points3.get(ptr).mDescription;
                        if (surf != null) {
                            // landmarks.add(points.get(ptr).mLocation);
                            System.out.println("For surface Added: " + points3.get(ptr).mLocation + " " + surf + " " + points3.get(ptr).mType);
                            // instructions.add("Presence of "+typeofhighway);
                            //tts.speak(surf + "surface at " + points.get(ptr).mLocation, TextToSpeech.QUEUE_FLUSH, null);

                        }
                        // tagdescriptions.put(points.get(0).mId, loc.getLatitude()+","+loc.getLongitude()+":"+points.get(0).mDescription);
                        // System.out.println("Tags for: "+points.get(ptr).mId+" -> "+points.get(ptr).mCategory+" "+points.get(ptr).mType+" "+points.get(ptr).mLocation.toDoubleString()+" "+points.get(ptr).mDescription);
                        ptr++;
                    }

                    landmarks.add(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
                    System.out.println("Added: " + loc.getLatitude() + ", " + loc.getLongitude() + " " + road.mNodes.get(i).mInstructions);
                    instructions.add(removeUnnamed(road.mNodes.get(i).mInstructions));

                    timestamps.add(new Long(0));
                    tst.add(new Long(0));
                    osmNumInstructions = road.mNodes.size();
                    osmNextInstruction = 0;
                    prefetch_nextInstruction = 0;
                    if (loc.getLatitude() > max_latitude) {
                        max_latitude = loc.getLatitude();
                    }
                    if (loc.getLatitude() < min_latitude) {
                        min_latitude = loc.getLatitude();
                    }
                    if (loc.getLongitude() > max_longitude) {
                        max_longitude = loc.getLongitude();
                    }
                    if (loc.getLongitude() < min_longitude) {
                        min_longitude = loc.getLongitude();
                    }
                }
            }
            // System.exit(0);
            int max_latitude_int = (int) (max_latitude * 10000000);
            int max_longitude_int = (int) (max_longitude * 10000000);
            int min_latitude_int = (int) (min_latitude * 10000000);
            int min_longitude_int = (int) (min_longitude * 10000000);
//            Cursor c = db.rawQuery("SELECT * FROM myLocation WHERE lat BETWEEN " + (min_latitude_int) + " AND " + (max_latitude_int) + " AND long BETWEEN " + (min_longitude_int) + " AND " + (max_longitude_int), null);
            Cursor c = db.rawQuery("SELECT * FROM myLocation", null);
            while (c.moveToNext()) {
                double latitude = Double.parseDouble(c.getString(1)) / 10000000;
                double longitude = Double.parseDouble(c.getString(2)) / 10000000;
                String description = c.getString(3);
                landmarks.add(new GeoPoint(latitude, longitude));
                instructions.add(description);
                timestamps.add(new Long(0));
                tst.add(new Long(0));
            }
            if (roadstatusflag) {
                isNavigating = true;
                navigatingDistance = distanceToStr(road.mLength);
                tts.speak("Starting Navigation. Your location is " + navigatingDistance + " away.", TextToSpeech.QUEUE_FLUSH, null);
                button.setText("Stop");
                save_button.setText("Save this route");
            }
        } else {
            landmarks.clear();
            instructions.clear();
            timestamps.clear();
            tst.clear();
            isNavigating = false;
            button.setText("Start");
            save_button.setText("Saved routes");
        }
    }

    public void showDb(View view) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < landmarks.size(); i++) {
            buffer.append(i + " : " + instructions.get(i) + "\n");
            buffer.append("Lat: " + landmarks.get(i).getLatitude() + " Long: " + landmarks.get(i).getLongitude());
        }
        Intent i = new Intent(getBaseContext(), ShowDb.class);
        i.putExtra("db", buffer.toString());
        startActivity(i);
//        Toast.makeText(getApplicationContext(),buffer.toString(),Toast.LENGTH_LONG).show();
    }

    public String removeUnnamed(String instruction) {
//        String newinstruction = instruction.replaceAll("unnamed", "this");
        return instruction.replaceAll("(.*)waypoint(.*)", "You have arrived at your destination.");
//        return newinstruction;
    }

    public String distanceToStr(double length) {
        String result;
        if (length >= 100.0) {
            result = this.getString(org.osmdroid.bonuspack.R.string.osmbonuspack_format_distance_kilometers, (int) (length)) + ", ";
        } else if (length >= 1.0) {
            result = this.getString(org.osmdroid.bonuspack.R.string.osmbonuspack_format_distance_kilometers, Math.round(length * 10) / 10.0) + ", ";
        } else {
            result = this.getString(org.osmdroid.bonuspack.R.string.osmbonuspack_format_distance_meters, (int) (length * 1000)) + ", ";
        }
        return result;
    }


    public void exportDb(View view) {
        try {
            File file = new File(this.getExternalFilesDir(null), "trace" + UUID.randomUUID().toString() + ".txt");
            FileOutputStream fileOutput = new FileOutputStream(file);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutput);
            Cursor c = db.rawQuery("SELECT * from trackData;", null);
            while (c.moveToNext()) {
                outputStreamWriter.write(c.getString(0) + "\t" + c.getString(1) + "\t" + c.getString(2) + "\t" + c.getString(3) + "\t" + c.getString(4) + "\n");
//                Toast.makeText(getApplicationContext(), c.getString(1), Toast.LENGTH_LONG).show();
            }
            outputStreamWriter.flush();
            fileOutput.getFD().sync();
            outputStreamWriter.close();
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{file.getAbsolutePath()},
                    null,
                    null);
            db.execSQL("DELETE from trackData;");
            db.execSQL("UPDATE SQLITE_SEQUENCE SET SEQ=0 WHERE NAME='trackData';");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
        if (isNavigating) {
            try {
                File file = new File(this.getExternalFilesDir(null), "mypoints" + UUID.randomUUID().toString() + ".txt");

                FileOutputStream fileOutput = new FileOutputStream(file);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutput);
                for (int i = 0; i < landmarks.size(); i++) {
                    outputStreamWriter.write(i + 1 + "\t" + landmarks.get(i).getLatitude() + "\t" + landmarks.get(i).getLongitude() + "\n");
                }
                outputStreamWriter.flush();
                fileOutput.getFD().sync();
                outputStreamWriter.close();
                MediaScannerConnection.scanFile(
                        this,
                        new String[]{file.getAbsolutePath()},
                        null,
                        null);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        }
        Toast.makeText(getApplicationContext(), "Written to file", Toast.LENGTH_LONG).show();


    }


    //PRINT THE WHOLE DATABASE
    public void showMessage1(View view) {
        for (int i = 0; i < landmarks.size(); i++) {
            System.out.println("Landmarks: " + landmarks.get(i));
            System.out.println("Instructions: " + instructions.get(i));
        }
        Cursor c = db.rawQuery("SELECT * FROM myLocation", null);
        if (c.getCount() == 0) {
            System.out.println("No records found");
            return;
        }
        StringBuffer buffer = new StringBuffer();
        while (c.moveToNext()) {
            buffer.append("Latitude: " + c.getString(1) + "\n");
            buffer.append("Longitude: " + c.getString(2) + "\n");
            buffer.append("Description: " + c.getString(3) + "\n");
            buffer.append("Id: " + c.getString(0) + "\n");
            buffer.append("Previous Id: " + c.getString(5));
            buffer.append("Next Id: " + c.getString(6) + "\n");
        }

        buffer.append("Location: " + current_lat + " " + current_long + "\n");
        System.out.println("Location Details: \n" + buffer.toString());
        Cursor cc = db.rawQuery("SELECT * FROM locationByTag", null);
        while (cc.moveToNext()) {
            buffer.append("Tag id: ");
            buffer.append(cc.getString(1) + "\nNode id: ");
            buffer.append(cc.getString(2) + "\n");
        }
        Intent i = new Intent(getBaseContext(), ShowDb.class);
        i.putExtra("db", buffer.toString());
        startActivity(i);
//        Toast.makeText(getApplicationContext(),buffer.toString(),Toast.LENGTH_LONG).show();
    }


    // PRINT THE POINTS NEAR TO THE GIVEN POINT
    public void getLocalInfo(View view) {

        Toast.makeText(getApplicationContext(), "Lat: " + current_lat + " Long: " + current_long, Toast.LENGTH_LONG).show();
    }

    public void clearData(View view) {
        db.execSQL("DELETE FROM mylocation;");
        prev_id = 0;
        Toast.makeText(this, "Data Cleared", Toast.LENGTH_SHORT).show();
    }

    public void getLocalInfo() {
        // System.out.println("getLocalInfo function called");
//        gps.getLocation();
        if (isNavigating) {
            double lat_float = current_lat;
            double long_float = current_long;

            // added by sac
            // below for loop need to be tested
            // comment out this thing if there is some issue
            if (prefetch_nextInstruction < landmarks.size()) {
                int i = prefetch_nextInstruction;
                float[] comp1 = new float[1];
                float[] comp2 = new float[1];
                Location.distanceBetween(current_lat, current_long, landmarks.get(i).getLatitude(), landmarks.get(i).getLongitude(), comp1);
                Location.distanceBetween(previous_location.getLatitude(), previous_location.getLongitude(), landmarks.get(i).getLatitude(), landmarks.get(i).getLongitude(), comp2);
                if (Math.abs(tst.get(i) - System.currentTimeMillis()) > 60000) {
                    System.out.println(comp1[0] + " " + comp2[0]);
                    if (comp1[0] > 12.0) {
                        int x = (int) comp1[0];
                        int y = (int) (comp2[0]) - 12;
                        if (x <= y / 5) {
                            tts.speak("In " + x + " meters " + instructions.get(i), TextToSpeech.QUEUE_FLUSH, null);
                        } else if (x <= 3 * y / 6) {
                            tts.speak("Continue straight for another " + x + " meters", TextToSpeech.QUEUE_FLUSH, null);
                        } else if (x <= y + 10) {
                            tts.speak("You are on the correct path. Next turn will be in " + x + " meters", TextToSpeech.QUEUE_FLUSH, null);
                        }
                    } else if (Math.abs(tst.get(i)) <= Math.abs(timestamps.get(i))) {
                        prefetch_nextInstruction = i + 1;
                        previous_location = new GeoPoint(landmarks.get(i).getLatitude(), landmarks.get(i).getLongitude());
                    }
                    tst.set(i, System.currentTimeMillis());
                }
                /*if (comp1[0] < comp2[0]/4.0) {
                    if (true) {
                        // System.out.println("Lat_diff: " + Math.abs(landmarks.get(i).getLatitude() - lat_float));
                        // System.out.println("Long diff: " + Math.abs(landmarks.get(i).getLongitude() - long_float));
                        // Toast.makeText(getApplicationContext(), instructions.get(i), Toast.LENGTH_LONG).show();
                        // System.out.println("String found: " + instructions.get(i));
                        tts.speak("In "+((int) (comp1[0]))+" meters "+instructions.get(i), TextToSpeech.QUEUE_FLUSH, null);
                        // timestamps.set(i, System.currentTimeMillis());
                        if (i < osmNumInstructions - 1) {
                           prefetch_nextInstruction = i + 1;
                           previous_location = new GeoPoint(landmarks.get(i).getLatitude(), landmarks.get(i).getLongitude());
                        }
                        break;
                    }
                }*/
            }
            // comment out till here

            // this below section works
            db.execSQL("INSERT INTO trackData VALUES(NULL, " + (int) (current_lat * 10000000) + ", " + (int) (current_long * 10000000) + ", 1, " + System.currentTimeMillis() + ");");
            for (int i = 0; i < landmarks.size(); i++) {
                if (Math.abs(landmarks.get(i).getLatitude() - lat_float) < 0.0001 && Math.abs(landmarks.get(i).getLongitude() - long_float) < 0.0001) {
                    if (Math.abs(timestamps.get(i) - System.currentTimeMillis()) > 60000) {
                        System.out.println("Lat_diff: " + Math.abs(landmarks.get(i).getLatitude() - lat_float));
                        System.out.println("Long diff: " + Math.abs(landmarks.get(i).getLongitude() - long_float));
                        Toast.makeText(getApplicationContext(), instructions.get(i), Toast.LENGTH_LONG).show();
                        System.out.println("String found: " + instructions.get(i));
                        tts.speak(instructions.get(i), TextToSpeech.QUEUE_FLUSH, null);
                        timestamps.set(i, System.currentTimeMillis());
                        if (i < osmNumInstructions - 1) {
                            osmNextInstruction = i + 1;
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(1000); // Update location every second

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);


        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            current_lat = mLastLocation.getLatitude();
            current_long = mLastLocation.getLongitude();

        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        buildGoogleApiClient();
    }

    @Override
    public void onLocationChanged(Location location) {
        current_lat = location.getLatitude();
        current_long = location.getLongitude();
    }

    public void onDebugButton(View view) {
        Intent i = new Intent(getBaseContext(), Debug.class);
        startActivity(i);
    }

    public void onNextButton(View view) {
        if (isNavigating) {
            float[] results = new float[3];
            Location.distanceBetween(current_lat, current_long, landmarks.get(osmNextInstruction).getLatitude(), landmarks.get(osmNextInstruction).getLongitude(), results);
            tts.speak("After " + ((int) results[0]) + " meters, " + instructions.get(osmNextInstruction), TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public static String requestStringFromUrl(String url, String userAgent) {
        HttpConnection connection = new HttpConnection();
        if (userAgent != null)
            connection.setUserAgent(userAgent);
        connection.doGet(url);
        String result = connection.getContentAsString();
        connection.close();
        return result;
    }

    /** sends an http request, and returns the whole content result in a String.
     * @param url
     * @return the whole content, or null if any issue.
     */
    public static String requestStringFromUrl(String url) {
        return requestStringFromUrl(url, null);
    }

    public void aroundMe(View view) {
        String min_lat = "" + (current_lat - 0.0005);
        String max_lat = "" + (current_lat + 0.0005);
        String min_long = "" + (current_long - 0.0005);
        String max_long = "" + (current_long + 0.0005);
        String request_url = OSM_EDITING_URL + min_long + "," + min_lat + "," + max_long + "," +  max_lat;
        String raw_text = requestStringFromUrl(request_url);
        Toast.makeText(getApplicationContext(), raw_text, Toast.LENGTH_LONG).show();
        System.out.println(raw_text);
    }

    public void voice(View view) {

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
// Start the activity, the intent will be populated with the speech text
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }
}

