package com.example.computerv;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Animation rotateOpen;
    private Animation rotateClose;
    private Animation fromBottom;
    private Animation toBottom;
    private FloatingActionButton addButton;
    private FloatingActionButton imageButton;
    private FloatingActionButton cameraButton;
    private FloatingActionButton copyButton;
    private FloatingActionButton saveButton;
    private ImageView previewImage;
    private TextView textView;
    private EditText extractedText;
    private Spinner spinner;
    private boolean clicked = false;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<CropImageContractOptions> cropImage;
    Map<String, String> langs = new HashMap<String, String>() {{
        put("vie", "Vietnamese");
        put("eng", "English");
        put("chi_sim","Chinese (Simplified)");
    }};
    private String selectedLang = "eng";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        try {
            copyTessData();
        } catch (IOException e) {
            e.printStackTrace();
        }
        loadAnimation();
        findViews();
        regActivity();
        setSpinner();
        addButton.setOnClickListener(v-> onAddButtonClicked());
        imageButton.setOnClickListener(v -> openImage());
        cameraButton.setOnClickListener(v -> openCamera());
        copyButton.setOnClickListener(v -> copyToClipboard());
        saveButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter File Name");

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String fileName = input.getText().toString().trim();
                if (!fileName.isEmpty()) {
                    exportToWord(fileName);
                } else {
                    Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        });
    }

    private void exportToWord(String fileName) {
        try {
            String text = String.valueOf(extractedText.getText());
            XWPFDocument document = new XWPFDocument();
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(text);
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ImageToText");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File file = new File(directory, fileName + ".docx");
            FileOutputStream outputStream = new FileOutputStream(file);
            document.write(outputStream);
            outputStream.close();
            document.close();

            Toast.makeText(this, "File saved at: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Text", extractedText.getText());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void setSpinner() {
        List<String> languageList = new ArrayList<>(langs.values());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languageList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                String value = languageList.get(position);
                selectedLang = getKeyFromValue(langs, value);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public static String getKeyFromValue(Map<String, String> map, String value) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String toText(Bitmap bitmap){
        TessBaseAPI tess = new TessBaseAPI();
        String dataPath = new File(getExternalFilesDir(null).getPath()).getAbsolutePath();
        if (!tess.init(dataPath, selectedLang)) {
            tess.recycle();
            return null;
        }
        tess.setImage(bitmap);
        String text = tess.getUTF8Text();
        tess.recycle();
        return text;
    }

    public Bitmap uriToBitmap(Uri uri, Context context) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            InputStream inputStream = contentResolver.openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private void copyTessData() throws IOException {
        File tessDataDir = new File(getExternalFilesDir(null), "tessdata");
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs();
        }

        AssetManager assetManager = getAssets();
        String[] files = assetManager.list("tessdata");

        if (files != null) {
            for (String file : files) {
                Log.d("TAG", "Found: " + file);
                String filePath = "tessdata/" + file;
                if (isDirectory(assetManager, filePath)) {
                    File outDir = new File(tessDataDir, file);
                    if (!outDir.exists()) {
                        outDir.mkdirs();
                    }
                    copyDirectory(assetManager, filePath, outDir);
                } else {
                    InputStream in = assetManager.open(filePath);
                    File outFile = new File(tessDataDir, file);
                    if (!outFile.exists()) {
                        OutputStream out = new FileOutputStream(outFile);
                        copyFile(in, out);
                        in.close();
                        out.flush();
                        out.close();
                    }
                }
            }
        }
    }

    private boolean isDirectory(AssetManager assetManager, String path) {
        try {
            String[] files = assetManager.list(path);
            return files != null && files.length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private void copyDirectory(AssetManager assetManager, String sourceDir, File destDir) throws IOException {
        String[] files = assetManager.list(sourceDir);
        if (files != null) {
            for (String file : files) {
                String sourceFilePath = sourceDir + "/" + file;
                File destFile = new File(destDir, file);
                InputStream in = assetManager.open(sourceFilePath);
                if (isDirectory(assetManager, sourceFilePath)) {
                    if (!destFile.exists()) {
                        destFile.mkdirs();
                    }
                    copyDirectory(assetManager, sourceFilePath, destFile);
                } else {
                    OutputStream out = new FileOutputStream(destFile);
                    copyFile(in, out);
                    in.close();
                    out.flush();
                    out.close();
                }
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
    }
    private Uri imageUri;
    private void regActivity(){
        CropImageOptions cropImageOptions = new CropImageOptions();
        cropImageOptions.imageSourceIncludeGallery = true;
        cropImageOptions.imageSourceIncludeCamera = true;
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        CropImageContractOptions cropImageContractOptions = new CropImageContractOptions(uri, cropImageOptions);
                        cropImage.launch(cropImageContractOptions);
                    }
                }
        );
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // Use the stored image URI directly
                        if (imageUri != null) {

                            // Launch the crop activity using the URI
                            CropImageContractOptions cropImageContractOptions =
                                    new CropImageContractOptions(imageUri, cropImageOptions);
                            cropImage.launch(cropImageContractOptions);
                        }
                    }
                });
        cropImage = registerForActivityResult(
                new CropImageContract(),
                result -> {
                    if (result.isSuccessful()) {
                        Uri uri = result.getUriContent();
                        previewImage.setImageURI(uri);
                        previewImage.setVisibility(View.VISIBLE);
                        textView.setVisibility(View.VISIBLE);
                        extractedText.setVisibility(View.VISIBLE);
                        extractedText.setText(toText(uriToBitmap(uri,this)));
                    }
                }
        );
    }
    private void openImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
        //startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void openCamera() {
        imageUri = createImageUri(this);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraLauncher.launch(intent);
    }

    private Uri createImageUri(Context context) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Captured Image");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Image from Camera");
        return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private void onAddButtonClicked() {
        setVisibility(clicked);
        setAnimation(clicked);
        clicked = !clicked;
    }

    private void setAnimation(boolean clicked) {
        if(!clicked){
            imageButton.startAnimation(fromBottom);
            cameraButton.startAnimation(fromBottom);
            copyButton.startAnimation(fromBottom);
            saveButton.startAnimation(fromBottom);
            addButton.startAnimation(rotateOpen);
        }else{
            imageButton.startAnimation(toBottom);
            cameraButton.startAnimation(toBottom);
            copyButton.startAnimation(toBottom);
            saveButton.startAnimation(toBottom);
            addButton.startAnimation(rotateClose);
        }
    }

    private void setVisibility(boolean clicked) {
        if (!clicked){
            imageButton.setVisibility(View.VISIBLE);
            cameraButton.setVisibility(View.VISIBLE);
            copyButton.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.VISIBLE);
        }else{
            imageButton.setVisibility(View.INVISIBLE);
            cameraButton.setVisibility(View.INVISIBLE);
            copyButton.setVisibility(View.INVISIBLE);
            saveButton.setVisibility(View.INVISIBLE);
        }
    }

    private void findViews(){
        addButton = findViewById(R.id.addButton);
        imageButton = findViewById(R.id.imageButton);
        cameraButton = findViewById(R.id.cameraButton);
        copyButton = findViewById(R.id.copyButton);
        saveButton = findViewById(R.id.saveButton);
        previewImage = findViewById(R.id.previewImage);
        textView = findViewById(R.id.textView);
        extractedText = findViewById(R.id.extractedText);
        spinner = findViewById(R.id.spinner);
    }

    private void loadAnimation(){
        rotateOpen = AnimationUtils.loadAnimation(this, R.anim.open_animation);
        rotateClose = AnimationUtils.loadAnimation(this, R.anim.close_animation);
        fromBottom = AnimationUtils.loadAnimation(this, R.anim.from_bottom);
        toBottom = AnimationUtils.loadAnimation(this, R.anim.to_bottom);
    }
}