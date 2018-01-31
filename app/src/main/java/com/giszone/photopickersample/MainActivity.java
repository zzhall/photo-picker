package com.giszone.photopickersample;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.giszone.photopicker.ImageCaptureManager;
import com.giszone.photopicker.PhotoPickerActivity;
import com.giszone.photopicker.PhotoPreviewActivity;
import com.giszone.photopicker.SelectModel;
import com.giszone.photopicker.intent.PhotoPickerIntent;
import com.giszone.photopicker.intent.PhotoPreviewIntent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * PhotoPicker Demo
 *
 * @author giszone
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainTAG";

    private static final int REQUEST_CAMERA_CODE = 0x11;
    private static final int REQUEST_PREVIEW_CODE = 0x22;
    private static final int REQUEST_PERMISSIONS = 0x33;

    private static final int MAX_PHOTO_COUNT = 5;
    private static final String[] PERMISSIONS = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA};

    private ArrayList<String> imagePaths;// 实际的照片路径列表
    private ArrayList<String> tempPaths;// 实际的照片路径列表加一个空地址（作为添加按钮）
    private ImageCaptureManager captureManager; // 相机拍照处理类

    private GridView gridView;
    private GridAdapter gridAdapter;
    private PermissionsChecker mPermissionsChecker;

    private int columnWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageCaptureManager.mStorageDir = "Router/pictures";


        initViewAndListener();

        imagePaths = new ArrayList<>();
        tempPaths = new ArrayList<>();
        loadAdapter();
        mPermissionsChecker = new PermissionsChecker(this);
    }

    private void initViewAndListener() {
        gridView = findViewById(R.id.gridView);

        int cols = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().densityDpi;
        cols = cols < 3 ? 3 : cols;
        gridView.setNumColumns(cols);

        // Item Width
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int gridPadding = getResources().getDimensionPixelOffset(R.dimen.grid_padding);
        int columnSpace = getResources().getDimensionPixelOffset(R.dimen.space_size);
        columnWidth = (screenWidth - 2 * gridPadding - columnSpace * (cols - 1)) / cols;

        // preview
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == parent.getCount() - 1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mPermissionsChecker.lacksPermissions(PERMISSIONS)) {
                    startPermissionsActivity();
                } else {
                    openAlbum();
                }
            } else {
                PhotoPreviewIntent intent = new PhotoPreviewIntent(MainActivity.this);
                intent.setCurrentItem(position);
                intent.setPhotoPaths(imagePaths);
                startActivityForResult(intent, REQUEST_PREVIEW_CODE);
            }
        });

        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position == parent.getCount() - 1) {
                return false;
            }
            imagePaths.remove(position);
            loadAdapter();
            return true;
        });
    }

    private void openAlbum() {
        PhotoPickerIntent intent = new PhotoPickerIntent(MainActivity.this);
        intent.setSelectModel(SelectModel.MULTI);
        intent.setShowCarema(true); // 是否显示拍照
        intent.setMaxTotal(MAX_PHOTO_COUNT); // 最多选择照片数量，默认为9
        intent.setSelectedPaths(imagePaths); // 已选中的照片地址， 用于回显选中状态
        startActivityForResult(intent, REQUEST_CAMERA_CODE);
    }

    private void openCamera() {
        if (imagePaths.size() >= MAX_PHOTO_COUNT) {
            Toast.makeText(this, "已达到" + MAX_PHOTO_COUNT + "张，请删除部分再拍照！", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (captureManager == null) {
                captureManager = new ImageCaptureManager(MainActivity.this);
            }
            Intent intent = captureManager.dispatchTakePictureIntent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); //添加这一句表示对目标应用临时授权该Uri所代表的文件
            }
            startActivityForResult(intent, ImageCaptureManager.REQUEST_TAKE_PHOTO);
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, com.giszone.photopicker.R.string.msg_no_camera, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void startPermissionsActivity() {
        PermissionsActivity.startActivityForResult(this, REQUEST_PERMISSIONS, PERMISSIONS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                // 选择照片
                case REQUEST_CAMERA_CODE:
                    reloadAdapter(data.getStringArrayListExtra(PhotoPickerActivity.EXTRA_RESULT));
                    break;
                // 预览
                case REQUEST_PREVIEW_CODE:
                    reloadAdapter(data.getStringArrayListExtra(PhotoPreviewActivity.EXTRA_RESULT));
                    break;
                // 调用相机拍照
                case ImageCaptureManager.REQUEST_TAKE_PHOTO:
                    if (captureManager.getCurrentPhotoPath() != null) {
                        captureManager.galleryAddPic();
                        imagePaths.add(captureManager.getCurrentPhotoPath());
                        loadAdapter();
                    }
                    break;
                // 权限申请
                case REQUEST_PERMISSIONS:
                    openAlbum();
                    break;

            }
        }
    }

    private void loadAdapter() {
        tempPaths.clear();
        tempPaths.addAll(imagePaths);
        tempPaths.add("");
        if (gridAdapter == null) {
            gridAdapter = new GridAdapter(tempPaths);
            gridView.setAdapter(gridAdapter);
        } else {
            gridAdapter.notifyDataSetChanged();
        }
    }

    private void reloadAdapter(ArrayList<String> paths) {
        Log.d(TAG, "MainActivity.reloadAdapter: " + paths.toString());
        if (imagePaths == null) {
            imagePaths = new ArrayList<>();
        }
        imagePaths.clear();
        imagePaths.addAll(paths);

        loadAdapter();
    }


    private class GridAdapter extends BaseAdapter {
        private ArrayList<String> listUrls;

        public GridAdapter(ArrayList<String> listUrls) {
            this.listUrls = listUrls;
        }

        @Override
        public int getCount() {
            return listUrls.size();
        }

        @Override
        public String getItem(int position) {
            return listUrls.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_image, parent, false);
                imageView = convertView.findViewById(R.id.imageView);
                convertView.setTag(imageView);
                // 重置ImageView宽高
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(columnWidth, columnWidth);
                imageView.setLayoutParams(params);
            } else {
                imageView = (ImageView) convertView.getTag();
            }

            if (position != getCount() - 1) {
                Glide.with(MainActivity.this)
                        .load(new File(getItem(position)))
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.default_error)
                                .error(R.drawable.default_error)
                                .centerCrop())
                        .transition(new DrawableTransitionOptions().crossFade())
                        .into(imageView);
            } else {
                Glide.with(MainActivity.this)
                        .load(new File(getItem(position)))
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_add)
                                .error(R.drawable.ic_add)
                                .centerCrop())
                        .transition(new DrawableTransitionOptions().crossFade())
                        .into(imageView);
            }
            return convertView;
        }
    }
}
