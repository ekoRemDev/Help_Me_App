package com.example.helpme.Activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.helpme.External_Models.AccurateLocationAsync;
import com.example.helpme.External_Models.ConnectNearby;
import com.example.helpme.External_Models.FetchAddressIntentService;
import com.example.helpme.External_Models.LocationsFetch;
import com.example.helpme.Extras.Constants;
import com.example.helpme.Extras.Permissions;
import com.example.helpme.Models.Help;
import com.example.helpme.Models.Photo;
import com.example.helpme.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.text.SimpleDateFormat;

public class PostActivity extends AppCompatActivity {

    public Help helpPost;

    DatabaseReference reference;

    private TextView postText;
    private Button takePhotoBtn;
    private Button takeLocationBtn;

    private LocationsFetch locationsFetch; public LocationsFetch getLocationsFetch(){ return this.locationsFetch; }
    private AccurateLocationAsync accurateLocationAsync;

    public static boolean postClicked = false;
    public static boolean customLocationTaken = false;
    private boolean currentLocationReceived = false;
    private boolean addressFetched = false;

    private Boolean photoSent = false;

    private Photo photo;
    private static final String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int PERMISSIONS_REQUEST_CODE = 337;

    private Permissions permissionObject;

    public static Location customLocation = new Location("maps");


    /**geo-coding receiver*/

    class AddressResultReceiver extends ResultReceiver {

        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if(resultData==null){
                Log.d(Constants.ADDRESS_LOG, "PostActivity->AddressReceiver->onReceiveResult: null resultData");
                return;
            }

            String addressOutput = resultData.getString(Constants.GEO_POST_ADDRESS); //receive data from the FetchAddressIntentService
            if(addressOutput==null)
                addressOutput = "no accurate address received";

            Log.d(Constants.ADDRESS_LOG,"PostActivity->AddressReceiver->onReceiveResult: address received = "+addressOutput);

            // do something with the @param addressOutput
            PostActivity.this.helpPost.setCurrent_address(addressOutput);

            if(PostActivity.postClicked) {
                //upload to db here
                Log.d(Constants.DB_LOG, "onReceiveResult: addHelpToDB() called from receiver");
                PostActivity.this.addHelpToDB();
            }
            else{
                //notify at postClick
                PostActivity.this.addressFetched = true;
            }

        }
    }

    AddressResultReceiver addressResultReceiver = new AddressResultReceiver(new Handler());

    /**/


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        postText = findViewById(R.id.editText_description_post);
        takePhotoBtn = findViewById(R.id.btn_take_photo);
        takeLocationBtn = findViewById(R.id.btn_pick_location);

        init();
    }

    private void init(){

        postClicked = false;
        customLocationTaken = false;
        currentLocationReceived = false;
        addressFetched = false;

        permissionObject = new Permissions(this, permissions, PERMISSIONS_REQUEST_CODE);

        ConnectNearby.postActivity = this; //set the new activity
        helpPost = new Help();

        locationsFetch = new LocationsFetch(this);
        locationsFetch.checkDeviceLocationSettings();

        accurateLocationAsync = new AccurateLocationAsync(this);

        //Log.d(Constants.LOCATION_LOG, "onResume: start aync task");
        accurateLocationAsync.execute(locationsFetch);
    }


    @Override
    protected void onResume() {
        super.onResume();

        //locationsFetch.startLocationUpdates();
        //if(accurateLocationAsync.isCancelled())
            //accurateLocationAsync.execute(locationsFetch);
        //Log.d(Constants.LOCATION_LOG, "PostActivity onResume: location request initiated");

    }

    @Override
    protected void onPause() {
        super.onPause();

        //if wifi + location + necessary location settings not met location update stopped here
        Log.d(Constants.NEARBY_LOG, "onPause: called");
        //accurateLocationAsync.cancel(true);
        //locationsFetch.stopLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {

            case Constants.LOCATION_CHECK_CODE:

                if (Activity.RESULT_OK == resultCode) {
                    Constants.IS_LOCATION_ENABLED = true;

                    currentLocationReceived = true;

                    Log.d(Constants.LOCATION_LOG, "onActivityResult: location enabled");
                }

                else if(Activity.RESULT_CANCELED == resultCode){
                    Log.d(Constants.LOCATION_LOG, "onActivityResult: user picked no or wifi is off");

                    currentLocationReceived = false;

                    Constants.IS_LOCATION_ENABLED = false;

                    //TODO: show dialog and open settings for manual location enabling
                    Toast.makeText(this,"Please Turn On Locations",Toast.LENGTH_LONG).show();
                }

                break;

            case Constants.REQUEST_TAKE_PHOTO:
                Log.d("COON","Reached!");
                if(Activity.RESULT_OK == resultCode){
                    //called after photo is taken successfully

                    takePhotoBtn.setText(R.string.photo_taken);
                    takePhotoBtn.setEnabled(false);

                    Log.d("COON","Reached! 2");
                    Log.d(Constants.PHOTO_LOG, "onActivityResult: camera open success");

                    try {
                        photo.compressPhotoFile();

                        //load photo into ConnectNearby class
                        ConnectNearby.photoFile = photo.getCompressPhotoFile();
                        ConnectNearby.photoFileUri = Uri.fromFile(photo.getCompressPhotoFile());
                        photoSent = true;


                        //upload to database
                        Uri imageUri = Uri.fromFile(photo.getCompressPhotoFile());
                        Log.d("COON","img data"+imageUri.toString());
                        Log.d(Constants.DB_LOG, "onActivityResult: db upload image uri = "
                                +imageUri.toString());
                        //

                        StorageReference mStorageRef;
                        mStorageRef = FirebaseStorage.getInstance().getReference().child("uploads");
                        final DatabaseReference reference = FirebaseDatabase.getInstance().getReference().child("helps");
                        final String photo_name_id = reference.push().getKey();
                        final StorageReference fileReference = mStorageRef.child(photo_name_id+".jpg");



                        fileReference.putFile(imageUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                fileReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri uri) {
                                        Toast.makeText(getApplicationContext(),"Photo Processed Successfully!",Toast.LENGTH_SHORT).show();
                                        Log.d("COON","Reached! 6");
                                        Log.d(Constants.DB_LOG, "onSuccess: file upload success url = "+uri.toString()+"?");
                                        helpPost.setPhoto_path(uri.toString());

                                    }
                                });
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.d("","Failed!");
                                Log.d(Constants.DB_LOG, "onFailure: failed to upload ");
                            }
                        });



                        Log.d(Constants.NEARBY_LOG, "onActivityResult: photo compressed successfully uri = "
                                + ConnectNearby.photoFileUri);

                    } catch (IOException e) {

                        takePhotoBtn.setText(R.string.take_photo_button);
                        takePhotoBtn.setEnabled(true);

                        e.printStackTrace();
                    }

                }
                else if(Activity.RESULT_CANCELED == resultCode){
                    //called if user didn't take photo

                    Log.d(Constants.PHOTO_LOG, "onActivityResult: open camera intent failed");
                }

                break;

            default:
                break;

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode){

            case PERMISSIONS_REQUEST_CODE: {

                Log.d(Constants.PERMISSIONS_LOG, "PostActivity->onRequestPermissionsResult: case "+permissionObject.getPERMISSION_REQUEST_CODE()
                        +"permissions = "+permissions);

                permissionObject.resolvePermissions(permissions, grantResults,
                        getString(R.string.camera_permission)+"\n"
                        +getString(R.string.files_write_permission)
                        );

            }

        }
    }


    /**button click listeners*/

    public void pickLocationClick(View view) {

        if(Constants.isIsInternetEnabled(this)) {

            Intent intent = new Intent(this, MapsActivity.class);

            try {

                intent.putExtra(Constants.MAP_LATITUDE_KEY, locationsFetch.getBestLocation().getLatitude());
                intent.putExtra(Constants.MAP_LONGITUDE_KEY, locationsFetch.getBestLocation().getLongitude());

            }catch (NullPointerException e){

                Log.d(Constants.PICK_LOCATION_LOG, "pickLocationClick: map opened with location = null");

            }

            intent.putExtra(Constants.MARKER_VISIBILITY_KEY, false);

            startActivity(intent);
        }

        else{

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Active Internet required to select location from Map!")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(Constants.PICK_LOCATION_LOG, "onClick: user is turning on internet");
                            Toast.makeText(PostActivity.this, "please connect to internet and 'Pick A Location'", Toast.LENGTH_LONG)
                                    .show();
                        }
                    })
                    .setNegativeButton("Try Again", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            Log.d(Constants.PICK_LOCATION_LOG, "onClick: started asyncLocationFetch again!");

                            locationsFetch.checkDeviceLocationSettings();
                            new AccurateLocationAsync(PostActivity.this).execute(locationsFetch);

                        }
                    });

            builder.create().show();

        }

    }

    public void takePhotoClick(View view) {

        if(!permissionObject.checkPermissions())
            permissionObject.askPermissions();

        else{

            try {
                photo = new Photo(this);
                photo.takePhoto(); //this method invokes onActivityResult

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Unable to open camera. Try again please.", Toast.LENGTH_LONG).show();
                Log.d(Constants.PHOTO_LOG, "takePhotoClick: camera open failed = "+e.getMessage());
            }

        }

    }

    public void postClick(View view) {

        if(!postClicked) //TODO: use in AccurateLocationAsync class. show progress dialog only after postClick
            postClicked = true;

        if(locationsFetch.isLocationAccurate() || locationsFetch.isBestLocationTaken() /* || accurateLocationAsync.isAsyncLocationDone()*/) {

            if(locationsFetch.isLocationAccurate() || customLocationTaken || locationsFetch.getBestLocation().getAccuracy() <= 100) {

                ConnectNearby.message = postText.getText().toString();
                helpPost.setDescription(postText.getText().toString());
                postText.setText("");

                if (!photoSent) {
                    ConnectNearby.photoFile = null;
                    ConnectNearby.photoFileUri = null;
                    Log.d(Constants.NEARBY_LOG, "postClick: no photo sent setting ConnectNearby photos null");
                }

                //start discovery after reverse geo and db upload?
                Log.d(Constants.NEARBY_LOG, "postClick: start discovery");
                ConnectNearby.startDiscovery();

                showPostingView(); //do this only after start discovery

                if(customLocationTaken){

                    Log.d(Constants.PICK_LOCATION_LOG, "postClick: custom location was taken");

                    helpPost.setLatlong(customLocation.getLatitude()+" "+customLocation.getLongitude());
                    startAddressFetchService(customLocation);

                }

            }

            else{
                //let user chose proper location
                takeLocationBtn.setEnabled(true);
                Toast.makeText(this, "Accurate location not available! please Pick A Location", Toast.LENGTH_LONG).show();

                Log.d(Constants.PICK_LOCATION_LOG, "postClick: user prompted for custom location");
            }

        }

        else if(!currentLocationReceived){
            //let user chose proper location
            takeLocationBtn.setEnabled(true);
            Toast.makeText(this, "Accurate location not available! please Pick A Location", Toast.LENGTH_LONG).show();

            Log.d(Constants.PICK_LOCATION_LOG, "postClick: user prompted for custom location");
        }

        else
            Log.d(Constants.LOCATION_LOG, "postClick: location not accurate yet");


        if(Constants.isIsInternetEnabled(this) && addressFetched){

            //upload to db here or inside addressReceiver?
            Log.d(Constants.DB_LOG, "postClick: addHelpToDB() called at post press");
            addHelpToDB();

        }

    }

    private void showPostingView() {
        setContentView(R.layout.activity_post_posting);
        Log.d(Constants.NEW_VIEW_LOG, "showPostingView: new layout view ser");

        TextView desc, addrs;
        desc = findViewById(R.id.descriptionPostingText);
        addrs = findViewById(R.id.addressText);
        desc.setText(helpPost.getDescription());
        addrs.setText(helpPost.getLatlong());
        desc.setMovementMethod(new ScrollingMovementMethod());


        ImageView image = findViewById(R.id.imageViewPosting);
        if(photo!=null) {

            Bitmap bmp = BitmapFactory.decodeFile(photo.getCompressPhotoPath());
            image.setImageBitmap(bmp);
        }

    }


    public void cancelClick(View view) {

        if(Constants.IS_DISCOVERING) {
            Log.d(Constants.NEARBY_LOG, "PostActivity onPause: stop discovery");
            ConnectNearby.stopDiscovery();
        }

        finish();
    }




    /**database upload method*/



    private void addHelpToDB()
    {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        long date = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy/hh-mm-ss");
        String dateandtime = sdf.format(date);

        //Data to store in Real Time Database
        helpPost.setSeeker_name(trimmer(user.getEmail()));
        helpPost.setUser_id(user.getUid());
        helpPost.setDateandtime(dateandtime);



        if(!TextUtils.isEmpty(helpPost.getDescription()) && helpPost.getCurrent_address()!=null)
        {

            Log.d(Constants.DB_LOG, "addHelpToDB: uploading to database");
            reference = FirebaseDatabase.getInstance().getReference().child("helps");

            String help_id = reference.push().getKey();
            helpPost.setHelpId(help_id);
            reference.child(help_id).setValue(helpPost).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Toast.makeText(getApplicationContext(),"Help Posted!",Toast.LENGTH_SHORT).show();
                }
            });

        }

    }

    private String trimmer(String str) {
        String temp="";
        for(int i =0;i<str.length();i++)
        {
            if(str.charAt(i)!='@')
            {
                temp = temp+str.charAt(i);
            }
            else
            {
                break;
            }
        }
        return temp.toUpperCase();
    }



    /**start new intent methods*/

    public void startAddressFetchService(Location location){

        Intent intent = new Intent(PostActivity.this, FetchAddressIntentService.class);
        intent.putExtra(Constants.GEO_POST_RECEIVER, addressResultReceiver);
        intent.putExtra(Constants.POST_GEO_LOCATION,location);

        Log.d(Constants.ADDRESS_LOG, "startAddressFetchService: starting intent service");
        startService(intent);

    }


}
