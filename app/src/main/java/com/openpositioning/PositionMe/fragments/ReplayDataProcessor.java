package com.openpositioning.PositionMe.fragments;

import com.openpositioning.PositionMe.Traj;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import com.google.protobuf.TextFormat;
import com.google.protobuf.util.JsonFormat;


public class ReplayDataProcessor {


    // A simple method to check if a file seems to be text.
    // This example returns false if a null byte is found.
    public static boolean isTextFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            byte[] fileBytes = Files.readAllBytes(path);
            for (byte b : fileBytes) {
                if (b == 0) { // A null byte is a common sign of binary data
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }



//    public static void protoBinDecoder(String filePath) {
    public static void protoDecoder(File file) {
//        String filePath_sf = filePath.toString();
        String filePath = file.getAbsolutePath();

        if (isTextFile(filePath)) { // plain text format
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Traj.Trajectory.Builder trajBuilder = Traj.Trajectory.newBuilder();
            try {
                // Decrypt the text format data into the builder
//                TextFormat.merge(sb.toString(), trajBuilder);
                JsonFormat.parser().merge(sb.toString(), trajBuilder);
                Traj.Trajectory message = trajBuilder.build();
                System.out.println("Decoded message: " + message.toString());
//            } catch (TextFormat.ParseException e) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else { // bin format
//            File file = new File(filePath);
//            try (FileInputStream fis = new FileInputStream(file)) {
            try (FileInputStream fis = new FileInputStream(file)) {
                // use the parseFrom() method of Traj.Trajectory to parse binary data
                Traj.Trajectory message = Traj.Trajectory.parseFrom(fis);
                System.out.println("Decoded message: " + message.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void trajProcessing(Traj.Trajectory trajectory) {

    }
}