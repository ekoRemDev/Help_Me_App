package com.example.helpme.Extras;


public abstract class Constants {

    /**Global Variables*/
    public static boolean IS_SENDER = false;
    public static boolean IS_CONNECTED = false;
    public static boolean IS_ADVERTISING = false;
    public static boolean IS_DISCOVERING = false;
    public static boolean IS_LOCATION_ENABLED = false;
    /***/

    /**Log Tags*/
    public static final String PERMISSIONS_LOG = "permissions_debug";
    public static final String WIFI_LOG = "wifi_debug";
    public static final String NEARBY_LOG = "nearby_debug";
    public static final String RECEIVER_END_POST_ACTIVITY = "receiverend_activity";
    public static final String LOCATION_LOG = "location_debug";
    public static final String PHOTO_LOG = "photo_debug";

    public static final String DB_LOG = "database_debug";
    /***/

    /**Intent Tags*/
    public static final String RECEIVED_MESSAGE_KEY = "received_message";
    public static final String RECEIVED_PHOTO_PATH_KEY = "received_photo_path";
    public static final String RECEIVED_LOCATION_KEY = "received_location";
    /***/

    /**Result Codes*/
    public static final int LOCATION_CHECK_CODE = 100;
    public static final int REQUEST_TAKE_PHOTO = 101;
    /***/
}
