package com.mycompany.app;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

import com.pi4j.plugin.pisocamera.Camera;
import com.pi4j.plugin.pisocamera.Camera.PicConfig;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    private TextArea textArea;  // TextArea to display output in the GUI

    // I2C addresses and bases for the seesaw device
    private static final int SEESAW_STATUS_BASE = 0x00;
    private static final int SEESAW_STATUS_TEMP = 0x04;
    private static final int SEESAW_TOUCH_BASE = 0x0F;
    private static final int SEESAW_TOUCH_CHANNEL_OFFSET = 0x10;
    private int counter = 0;
    public static void main(String[] args) {
        launch(args);  // Launch the JavaFX application
    }

    public boolean begin(I2CDevice device) throws IOException {
        // Check if device is reachable
        return device.getAddress() == 0x36;
    }

    public float getTemp(I2CDevice device) throws IOException {
        try {
            // Prepare to read from the temperature register
            byte[] regAddress = {(byte) SEESAW_STATUS_BASE, (byte) SEESAW_STATUS_TEMP};
            device.write(regAddress, 0, 2);

            // Wait before reading
            Thread.sleep(10);

            // Read temperature data
            byte[] buf = new byte[4];
            device.read(buf, 0, 4);

            // Convert the read bytes to temperature
            int ret = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) | ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
            return (1.0f / (1 << 16)) * ret;

        } catch (InterruptedException e) {
            // Handle exceptions
            return -1.0f;
        }
    }

    public int touchRead(I2CDevice device, int pin) throws IOException {
        try {
            // Prepare the address to read touch from
            byte[] regAddress = {(byte) SEESAW_TOUCH_BASE, (byte) (SEESAW_TOUCH_CHANNEL_OFFSET + pin)};
            
            int ret = 65535;  // Initialize with an error value
            byte[] buf = new byte[2];

            // Retry mechanism for reliability
            for (int retry = 0; retry < 5; retry++) {
                device.write(regAddress, 0, 2);

                // Delay to wait for the device to process
                Thread.sleep(3 + retry);

                // Read touch data
                int bytesRead = device.read(buf, 0, 2);
                if (bytesRead == 2) {
                    // Convert the read bytes to touch value
                    ret = ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
                    break;
                }
            }
            return ret;

        } catch (InterruptedException e) {
            // Handle exceptions
            return -1;
        }
    }

    public void captureImageWithPiCamera() {
        try {
            Camera camera = new Camera(); // Initialize the camera

            // Define the configuration for the picture
            var config = PicConfig.Builder
                    .outputPath("/home/pi/captured_image" + counter + ".jpg")
                    .build();

            // Take a picture with the configuration
            camera.takeStill(config);

            counter++;  // Increment counter for the next image
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    
    

    @Override
    public void start(Stage stage) throws Exception {
        textArea = new TextArea();  // Initialize TextArea
        textArea.setEditable(false);  // Disable editing

        StackPane root = new StackPane();
        root.getChildren().add(textArea);  // Add TextArea to the scene graph

        Scene scene = new Scene(root, 600, 400);
        stage.setTitle("Moisture and Temperature");  // Set window title
        stage.setScene(scene);  // Set scene
        stage.show();  // Display the window

        // Initialize I2C bus and device
        I2CBus bus = I2CFactory.getInstance(I2CBus.BUS_1);
        I2CDevice device = bus.getDevice(0x36);

        // Check if the device is available
        if (!begin(device)) {
            textArea.appendText("ERROR! seesaw not found\n");
            return;
        }

        // Animation timer to read and update values
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                try {
                    float tempC = getTemp(device);
                    int capread = touchRead(device, 0);

                    if (capread > 1000) {
                        captureImageWithPiCamera();  // Capture an image if capacitance > 1000
                    }
            
                    // Format and display the readings
                    String output = String.format("Temperature: %.2f*C\nCapacitive: %d\n", tempC, capread);
                    textArea.appendText(output);

                } catch (IOException e) {
                    // Handle I/O errors
                    textArea.appendText("Failed to read from device: " + e.getMessage() + "\n");
                }
            }
        }.start();
    }
}
