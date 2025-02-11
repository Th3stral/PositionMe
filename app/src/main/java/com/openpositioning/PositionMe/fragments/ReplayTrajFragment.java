package com.openpositioning.PositionMe.fragments;

import android.graphics.Color;
import android.os.Bundle;
//import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.openpositioning.PositionMe.IndoorMapManager;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.Traj;
import com.openpositioning.PositionMe.UtilFunctions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ReplayTrajFragment extends Fragment {
    // For initialize map to replay with indoor map
    private GoogleMap replayMap;
    public IndoorMapManager indoorMapManager;

//    public ReplayDataProcessor ReplayDataProcessor;
    private LatLng startLoc;
    private Polyline polyline;
    private LatLng currentLocation;

    private float currentOrientation;
    private Marker orientationMarker;

    private Timer readPdrTimer;
    private TimerTask currTask;
    private static final long TimeInterval = 10;

    private Traj.Trajectory trajectory;
    private int trajSize;

    private ReplayDataProcessor.TrajRecorder trajProcessor;

    private List<LatLng> pdrLocList;
    private List<Traj.Motion_Sample> imuDataList;
    private List<Traj.Pressure_Sample> pressureSampleList;

    private int currStepCount = 0;

    private float currElevation;

    private int currProgress = 0;
    private int counterYaw = 0;
    private int counterPressure = 0;
    private int counterYawLimit = 0;

//    private int nextProgress;


    private SeekBar seekBar;
    private ImageButton replayButton;
    private ImageButton replayBackButton;
    private ImageButton playPauseButton;
    private ImageButton goToEndButton;

    private boolean isPlaying = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.trajProcessor = ReplayDataProcessor.TrajRecorder.getInstance();
        this.trajectory = trajProcessor.getReplayTraj();
        readPdrTimer = new Timer();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_replay, container, false);
        pdrLocList = ReplayDataProcessor.translatePdrPath(this.trajectory);
        imuDataList = this.trajectory.getImuDataList();
        pressureSampleList = this.trajectory.getPressureDataList();
        trajSize = imuDataList.size();
//        float[] startLoc = ReplayDataProcessor.getStartLocation(trajectory);
//        currentLocation = new LatLng(startLoc[0], startLoc[1]);

        // Initialize map/ indoor map
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.ReplayMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(@NonNull GoogleMap map) {
                    replayMap = map;
                    //Initialising the indoor map manager object
                    indoorMapManager = new IndoorMapManager(map);
                    // Setting map attributes
                    map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    map.getUiSettings().setCompassEnabled(true);
                    map.getUiSettings().setTiltGesturesEnabled(true);
                    map.getUiSettings().setRotateGesturesEnabled(true);
                    map.getUiSettings().setScrollGesturesEnabled(true);

                    // Add a marker at the start position and move the camera
                    PositionInitialization();

                    //Center the camera
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(startLoc, (float) 19f));
                    indoorMapManager.setCurrentLocation(startLoc);
                    //Showing an indication of available indoor maps using PolyLines
                    indoorMapManager.setIndicationOfIndoorMap();
                    }
            });
        }
        seekBar = rootView.findViewById(R.id.seekBar);
        seekBar.setMax(100);
        return rootView;
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ReplayBackButton();
        setupReplayButton();
        setupGoToEndButton();
        setupPlayPauseButton();
        ProgressView();

        currTask = createTimerTask();
        this.readPdrTimer.schedule(currTask, 0, TimeInterval);
    }

    private TimerTask createTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            drawPathView();
                        }
                    });
                }
            }
        };
    }

    public void PositionInitialization(){
        counterYaw = 0;
        counterPressure = 0;
        currProgress = 0;
        currStepCount = 0;
        if(polyline != null) { polyline.remove(); }
        if(orientationMarker != null) { orientationMarker.remove(); }
        startLoc = !pdrLocList.isEmpty() ? pdrLocList.get(0) : new LatLng(0,0);
        currElevation = trajectory.getPressureData(counterPressure).getEstimatedElevation();
        currentOrientation = imuDataList.get(counterYaw).getAzimuth();

        if (startLoc != null) {
            orientationMarker = replayMap.addMarker(new MarkerOptions().position(startLoc).title("Current Position")
                    .flat(true)
                    .icon(BitmapDescriptorFactory.fromBitmap(
                            UtilFunctions.getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_24)))
                    .zIndex(6));
            PolylineOptions polylineOptions=new PolylineOptions()
                    .color(Color.RED)
                    .add(startLoc)
                    .zIndex(6);
            polyline = replayMap.addPolyline(polylineOptions);
    }
}

    public TimerTask drawPathView() {

        // ===== break logic ===== //
        if (counterYaw >= imuDataList.size() - 1) {
            if (currTask != null) {
                currTask.cancel();
            }
            isPlaying = false;
            return null;
        }

        // ===== orientation value update logic ===== //
        // get base tick
        long relativeTBase = imuDataList.get(counterYaw).getRelativeTimestamp();

        float nextOrientation = trajectory.getImuData(counterYaw).getAzimuth();
        if (orientationMarker!=null && currentOrientation!= nextOrientation) {
            currentOrientation = nextOrientation;
            orientationMarker.setRotation((float) Math.toDegrees(currentOrientation));
        }

        // ===== pressure value update logic ===== //
        if (counterPressure < pressureSampleList.size() - 1) {
            // always take the next pressure sample
            Traj.Pressure_Sample nextPressureSample = pressureSampleList.get(counterPressure + 1);
            long nextTPressure = nextPressureSample.getRelativeTimestamp();
            float nextElevation = nextPressureSample.getEstimatedElevation();
            if (relativeTBase >= nextTPressure) {
                currElevation = nextElevation;
                counterPressure++;
            }
        } else {
            // Ensure the last pressure sample is used when counterPressure reaches the last index
            currElevation = pressureSampleList.get(counterPressure).getEstimatedElevation();
        }

        // ===== pdr value update logic ===== //
        int nextStepCount = imuDataList.get(counterYaw).getStepCount();
        if (currStepCount != nextStepCount) {
            currStepCount = nextStepCount;
            currentLocation = pdrLocList.get(currStepCount);

            // move the marker
            if (orientationMarker!=null) {
                orientationMarker.setPosition(currentLocation);
            }

            if (polyline!=null) {
                // get existing points
                List<LatLng> pointsMoved = polyline.getPoints();
                // add new point
                pointsMoved.add(currentLocation);
                polyline.setPoints(pointsMoved);
            }
        }

        // ===== progress bar update logic ===== //
        currProgress = (int) ((counterYaw * 100.0f) / trajSize);
        seekBar.setProgress(currProgress);

        // ===== counter update logic ===== //
        counterYaw++;
        return null;
    }

    public void ProgressView() {
        if (seekBar == null) return;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                // No actions required
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (currTask != null) {
                    currTask.cancel();
                    isPlaying = false;
                    playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int nextProgress = seekBar.getProgress();
                counterYawLimit = (int) ((nextProgress / 100.0f) * trajSize - 1);
                isPlaying = true;
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
//                if (currTask != null) {
//                    currTask.cancel();
//                    PositionInitialization();
//                }
                PositionInitialization();
                for (counterYaw = 0; counterYaw <= counterYawLimit; counterYaw++) {
                    drawPathView();
                }
                currTask = createTimerTask();
                readPdrTimer.schedule(currTask, 0, TimeInterval);
            }
        });
    }

    //return back to the last page/////////////////////////////////////////////////////
    private void ReplayBackButton() {
        replayBackButton = requireView().findViewById(R.id.ReplayBackButton);
        replayBackButton.setOnClickListener(view -> {
            if (requireActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }//////////////////////////////////////////////////////////////////////////////////


// play / pause button control
private void setupPlayPauseButton() {
    playPauseButton = requireView().findViewById(R.id.PlayPauseButton);
    playPauseButton.setOnClickListener(v -> {
        if (currTask != null) {
            currTask.cancel();
            if (isPlaying) {
                isPlaying = false;
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
            } else {
                if (currProgress == 100) {
                    PositionInitialization();
                    readPdrTimer = new Timer();
                }
                currTask = createTimerTask();
                readPdrTimer.schedule(currTask, 0, TimeInterval);
                isPlaying = true;
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            }
        }
    });
}

    private void setupReplayButton() {
        replayButton = requireView().findViewById(R.id.ReplayButton);
        replayButton.setOnClickListener(v -> {
            if (currTask != null) {
                currTask.cancel();
            }
            PositionInitialization();
            currTask = createTimerTask();
            readPdrTimer.schedule(currTask, 0, TimeInterval);
            isPlaying = true;
        });
    }
    private void setupGoToEndButton() {
        goToEndButton = requireView().findViewById(R.id.GoToEndButton);
        goToEndButton.setOnClickListener(v -> {
            try{
                if (currTask != null) {
                    currTask.cancel();
                }}
            catch (Exception e) {
                Log.e("GoToEnd", "Fail to cancel currTask",e);
            }
            if(seekBar.getProgress() != 100){
            counterYawLimit = trajSize - 1;
            try{
            for (counterYaw = 0; counterYaw < counterYawLimit; counterYaw++) { drawPathView(); }}
            catch (Exception e){
                Log.e("DrawLogic","Error Draw",e);
            }
            seekBar.setProgress(100);
            isPlaying = false;}
        });
    }
}