package com.openpositioning.PositionMe.fragments;

import android.graphics.Color;
import android.os.Bundle;
//import android.os.Handler;
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
    private LatLng start;
    private Polyline polyline;
    private LatLng currentLocation;
    private Marker orientationMarker;

    private Timer readPdrTimer;
    private TimerTask currTask;
    private static final long TimeInterval = 10;

    private Traj.Trajectory trajectory;
    private int trajSize;

    // data input
    private List<LatLng> pdrLocList;
    private List<Traj.Motion_Sample> imuDataList;
    private List<Traj.Pressure_Sample> pressureSampleList;

    private int stepCount;
    private int currProgress = 0;
    private int counterYaw = 0;
    private int counterPressure = 0;
    private int counterYawLimit = 0;
    private float currElev;

    // fragment components
    private SeekBar seekBar;
    private ImageButton replayButton;
    private ImageButton replayBackButton;
    private ImageButton playPauseButton;
    private ImageButton goToEndButton;

    private boolean isPlaying = true;          //判断是否在播放/播放结束

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readPdrTimer = new Timer();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_replay, container, false);
        this.trajectory = processing.decodexx(File file);
        pdrLocList = processing.getPdrLoc(this.trajectory);
        imuDataList = this.trajectory.getImuDataList();
        pressureSampleList = this.trajectory.getPressureDataList();
        trajSize = imuDataList.size();

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
                    start = !pdrLocList.isEmpty() ? pdrLocList.get(0) : null;
                    if (start != null) {
                        orientationMarker = map.addMarker(new MarkerOptions().position(start).title("Current Position")
                                .flat(true)
                                .icon(BitmapDescriptorFactory.fromBitmap(
                                        UtilFunctions.getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_24)))
                                .zIndex(1f));
                        //Center the camera
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, (float) 19f));
                        indoorMapManager.setCurrentLocation(start);
                        //Showing an indication of available indoor maps using PolyLines
                        indoorMapManager.setIndicationOfIndoorMap();
                    }
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
    public TimerTask drawPathView() {
        if (counterYaw >= imuDataList.size() - 1 || counterPressure >= pressureSampleList.size() - 1) {
            if (currTask != null) {
                currTask.cancel();
            }
            isPlaying = false;
        }
        long relativeTBase = imuDataList.get(counterYaw).getRelativeTimestamp();
//        Traj.Pressure_Sample currPressureSample = pressureSampleList.get(counterPressure);
        Traj.Pressure_Sample nextPressureSample = pressureSampleList.get(counterPressure + 1);
//        float currElev; = currPressureSample.getElev();
        long nextTPressure = nextPressureSample.getRelativeTimestamp();

        if (relativeTBase >= nextTPressure) {
            if (counterPressure < pressureSampleList.size()) {
                currElev = nextPressureSample.getElev();
                counterPressure++;
                if (stepCount != imuDataList.get(counterYaw).getStepCount()) {
                    currentLocation = pdrLocList.get(counterYaw);
                    if (orientationMarker!=null) {
                        orientationMarker.setRotation((float) );
                        orientationMarker.setPosition(currentLocation);
                    }
                    PolylineOptions polylineOptions = new PolylineOptions()
                            .color(Color.RED)
                            .add(currentLocation)
                            .zIndex(1f);
                    polyline = replayMap.addPolyline(polylineOptions);
                }
            }
        }
        stepCount = imuDataList.get(counterYaw).getStepCount();
        currProgress = (int) ((counterYaw * 100.0f) / trajSize);
        seekBar.setProgress(currProgress);
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
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int nextProgress = seekBar.getProgress();
                counterYawLimit = (int) ((nextProgress / 100.0f) * trajSize - 1);
                for (int i = 0; i < counterYawLimit; i++) {
                    long relativeTBase = imuDataList.get(i).getRelativeTimestamp();

                    Traj.Pressure_Sample nextPressureSample = pressureSampleList.get(counterPressure + 1);
                    long nextTPressure = nextPressureSample.getRelativeTimestamp();

                    if (relativeTBase >= nextTPressure) {
                        if (counterPressure < pressureSampleList.size()) {
                            currElev = nextPressureSample.getElev();
                            counterPressure++;
                            if (stepCount != imuDataList.get(i).getStepCount()) {
                                currentLocation = pdrLocList.get(i);
                                PolylineOptions polylineOptions = new PolylineOptions()
                                        .color(Color.RED)
                                        .add(currentLocation)
                                        .zIndex(1f);
                                polyline = replayMap.addPolyline(polylineOptions);
                            }
                        }
                    }
                    stepCount = imuDataList.get(i).getStepCount();
                }
                currTask = drawPathView();
                readPdrTimer.schedule(currTask, 0, TimeInterval);
            }
        });
    }

    //return back to the last page
    private void ReplayBackButton() {
        replayBackButton = requireView().findViewById(R.id.ReplayBackButton);
        replayBackButton.setOnClickListener(view -> {
            if (requireActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                requireActivity().finish();
            }
        });
    }


// play / pause button control
private void setupPlayPauseButton() {
    playPauseButton = requireView().findViewById(R.id.PlayPauseButton);
    playPauseButton.setOnClickListener(v -> {
        if (isPlaying) {
            if (currTask != null) {
                currTask.cancel();
            }
            isPlaying = false;
            playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        } else {
            currTask = createTimerTask();
            readPdrTimer.schedule(currTask, 0, TimeInterval);
            isPlaying = true;
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        }
    });
}

    private void setupReplayButton() {
        replayButton = requireView().findViewById(R.id.ReplayButton);
        replayButton.setOnClickListener(v -> {
            if (currTask != null) {
                currTask.cancel();
            }
            counterYaw = 0;
            counterPressure = 0;
            currProgress = 0;
            stepCount = 0;
            if(polyline != null) { polyline.remove(); }

            currTask = createTimerTask();
            readPdrTimer.schedule(currTask, 0, TimeInterval);
            isPlaying = true;
        });
    }
    private void setupGoToEndButton() {
        goToEndButton = requireView().findViewById(R.id.GoToEndButton);
        goToEndButton.setOnClickListener(v -> {
            if (currTask != null) {
                currTask.cancel();
            }
            counterYawLimit = trajSize - 1;
            for (int i = 0; i < counterYawLimit; i++) {
                long relativeTBase = imuDataList.get(i).getRelativeTimestamp();

                Traj.Pressure_Sample nextPressureSample = pressureSampleList.get(counterPressure + 1);
                long nextTPressure = nextPressureSample.getRelativeTimestamp();

                if (relativeTBase >= nextTPressure) {
                    if (counterPressure < pressureSampleList.size()) {
                        currElev = nextPressureSample.getElev();
                        counterPressure++;
                        if (stepCount != imuDataList.get(i).getStepCount()) {
                            currentLocation = pdrLocList.get(i);
                            PolylineOptions polylineOptions = new PolylineOptions()
                                    .color(Color.RED)
                                    .add(currentLocation)
                                    .zIndex(1f);
                            polyline = replayMap.addPolyline(polylineOptions);
                        }
                    }
                }
                stepCount = imuDataList.get(i).getStepCount();
            }
            seekBar.setProgress(100);
            isPlaying = false;
        });
    }
}