package com.galix.opentiktok.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.galix.opentiktok.R;
import com.galix.opentiktok.avcore.AVAudio;
import com.galix.opentiktok.avcore.AVSticker;
import com.galix.opentiktok.avcore.AVEngine;
import com.galix.opentiktok.avcore.AVVideo;
import com.galix.opentiktok.avcore.AVWord;
import com.galix.opentiktok.render.ImageViewRender;
import com.galix.opentiktok.render.TextRender;
import com.galix.opentiktok.util.GestureUtils;
import com.galix.opentiktok.util.GifDecoder;
import com.galix.opentiktok.util.VideoUtil;

import java.util.LinkedList;

import static androidx.recyclerview.widget.RecyclerView.HORIZONTAL;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.SEEK;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.START;

/**
 * 视频编辑界面
 *
 * @Author Galis
 * @Date 2022.01.15
 */
public class VideoEditActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("arcore");
    }

    private static final String TAG = VideoEditActivity.class.getSimpleName();
    private static final int REQUEST_CODE = 1;
    private static final int DRAG_HEAD = 0;
    private static final int DRAG_FOOT = 1;
    private static final int DRAG_IMG = 2;
    private static final int DRAG_ADD = 3;
    private static final int DRAG_MUTE = 4;
    private static final int DRAG_SPLIT = 5;
    private static final int THUMB_SLOT_WIDTH = 80;

    private LinkedList<ThumbInfo> mThumbsList;
    private LinkedList<Integer> mStickerList;//贴纸
    private GLSurfaceView mSurfaceView;
    private RecyclerView mTabRecyclerView;
    private HorizontalScrollView mThumbDragRecyclerView;
    private RecyclerView mStickerRecyclerView;

    private ImageView mStickerView;
    private EditText mEditTextView;
    private GifDecoder mGifDecoder;
    private TextView mWordView;
    private TextView mTimeInfo;
    private ImageView mPlayBtn;
    private ImageView mFullScreenBtn;
    private int mScrollX = 0;
    private AVEngine mAVEngine;

    //底部ICON info
    private static final int[] TAB_INFO_LIST = {
            R.drawable.icon_video_cut, R.string.tab_cut,
            R.drawable.icon_adjust, R.string.tab_audio,
            R.drawable.icon_adjust, R.string.tab_text,
            R.drawable.icon_adjust, R.string.tab_sticker,
            R.drawable.icon_adjust, R.string.tab_inner_picture,
            R.drawable.icon_adjust, R.string.tab_magic,
            R.drawable.icon_filter, R.string.tab_filter,
            R.drawable.icon_adjust, R.string.tab_ratio,
            R.drawable.icon_background, R.string.tab_background,
            R.drawable.icon_adjust, R.string.tab_adjust
    };

    public static void start(Context ctx) {
        Intent intent = new Intent(ctx, VideoEditActivity.class);
        ctx.startActivity(intent);
    }


    private class ThumbInfo {
        public int type;
        public String imgPath;
    }

    private class ImageViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;
        public TextView textView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private class ThumbViewHolder extends RecyclerView.ViewHolder {
        public View view1;
        public View view2;

        public ThumbViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }


    //UI回调
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);

        mStickerView = findViewById(R.id.image_sticker);
        GestureUtils.setupView(mStickerView, new Rect(0, 0, 1920, 1080));
        mEditTextView = findViewById(R.id.edit_tip);
        GestureUtils.setupView(mEditTextView, new Rect(0, 0, 1920, 1080));
        mWordView = findViewById(R.id.tv_word);
        mSurfaceView = findViewById(R.id.glsurface_preview);
        mTimeInfo = findViewById(R.id.text_duration);
        mPlayBtn = findViewById(R.id.image_play);
        mPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAVEngine.startPause();
                freshUI();
            }
        });
        mFullScreenBtn = findViewById(R.id.image_fullscreen);
        mTabRecyclerView = findViewById(R.id.recyclerview_tab_mode);
        mTabRecyclerView.setLayoutManager(new LinearLayoutManager(this, HORIZONTAL, false));
        mTabRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View layout = getLayoutInflater().inflate(R.layout.layout_tab_item, parent, false);
                ImageViewHolder imageViewHolder = new ImageViewHolder(layout);
                imageViewHolder.itemView.getLayoutParams().width = (int) (60 * getResources().getDisplayMetrics().density);
                imageViewHolder.itemView.getLayoutParams().height = (int) (60 * getResources().getDisplayMetrics().density);
                imageViewHolder.imageView = layout.findViewById(R.id.image_video_thumb);
                imageViewHolder.textView = layout.findViewById(R.id.text_video_info);
                return imageViewHolder;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageViewHolder imageViewHolder = (ImageViewHolder) holder;
                imageViewHolder.imageView.setImageResource(TAB_INFO_LIST[2 * position]);
                imageViewHolder.textView.setText(TAB_INFO_LIST[2 * position + 1]);
                imageViewHolder.itemView.setOnClickListener(v -> {
                    if (TAB_INFO_LIST[2 * position + 1] == R.string.tab_sticker) {
                        mStickerRecyclerView.setVisibility(View.VISIBLE);
                    } else if (TAB_INFO_LIST[2 * position + 1] == R.string.tab_text) {
                        mAVEngine.addComponent(new AVWord(mAVEngine.getMainClock(), mAVEngine.getMainClock() + 5000000,
                                new TextRender(mEditTextView)));
                    } else {
                        mStickerRecyclerView.setVisibility(View.GONE);
                        Toast.makeText(VideoEditActivity.this, "待实现", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public int getItemCount() {
                return TAB_INFO_LIST.length / 2;
            }
        });
        mTabRecyclerView.getAdapter().notifyDataSetChanged();

        mThumbDragRecyclerView = findViewById(R.id.recyclerview_drag_thumb);
        mStickerList = new LinkedList<>();
        mStickerList.add(R.raw.aini);
        mStickerList.add(R.raw.buyuebuyue);
        mStickerList.add(R.raw.burangwo);
        mStickerList.add(R.raw.dengliao);
        mStickerList.add(R.raw.gandepiaoliang);
        mStickerList.add(R.raw.nizabushagntian);
        mStickerRecyclerView = findViewById(R.id.recyclerview_sticker);
        mStickerRecyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        mStickerRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView view = new ImageView(parent.getContext());
                view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                view.setLayoutParams(new RecyclerView.LayoutParams(parent.getMeasuredWidth() / 4,
                        parent.getMeasuredWidth() / 4));
                return new ThumbViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Glide.with(VideoEditActivity.this)
                        .load(mStickerList.get(position))
                        .asGif()
                        .into((ImageView) holder.itemView);
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mStickerView.setVisibility(View.VISIBLE);
                        mAVEngine.addComponent(new AVSticker(mAVEngine.getMainClock(), mAVEngine.getMainClock() + 2000000,//TODO
                                getResources().openRawResource(mStickerList.get(position)),
                                new ImageViewRender(mStickerView)));
                        mStickerRecyclerView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return mStickerList.size();
            }
        });

        //Actionbar
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        //初始化Thumb信息
        mAVEngine = AVEngine.getVideoEngine();
        mAVEngine.configure(mSurfaceView);
        long startTime = 0;
        LinearLayout linearLayout = findViewById(R.id.linear_parent);
        View head = new View(this);
        head.setLayoutParams(new LinearLayout.LayoutParams(getWindowManager().getCurrentWindowMetrics().getBounds().width() / 2, ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.addView(head);
        View tail = new View(this);
        tail.setLayoutParams(new LinearLayout.LayoutParams(getWindowManager().getCurrentWindowMetrics().getBounds().width() / 2, ViewGroup.LayoutParams.MATCH_PARENT));
        for (VideoUtil.FileEntry fileEntry : VideoUtil.mTargetFiles) {
            mAVEngine.addComponent(new AVVideo(startTime, startTime + fileEntry.duration, fileEntry.adjustPath, mAVEngine.nextValidTexture(), null));
            mAVEngine.addComponent(new AVAudio(startTime, startTime + fileEntry.duration, fileEntry.adjustPath, null));
            ComponentView view = new ComponentView.Builder(this)
                    .setComponent(new AVVideo(startTime, fileEntry.duration, fileEntry.adjustPath, null))
                    .setPaddingColor(Color.YELLOW)
                    .setTileSize((int) (THUMB_SLOT_WIDTH * getResources().getDisplayMetrics().density))
                    .setPaddingLeftRight(0)
                    .setPaddingTopBottom(0)
                    .build();
            startTime += fileEntry.duration;
            linearLayout.addView(view);
        }
        linearLayout.addView(tail);
        mAVEngine.setOnFrameUpdateCallback(() -> freshUI());
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAVEngine.release();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_video_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_export:
                break;
            case R.id.action_pixel:
                break;
            default:
                break;
        }
        return true;
    }

    //不同线程都可以刷新UI
    private void freshUI() {
        getWindow().getDecorView().post(() -> {
            AVEngine.VideoState mVideoState = AVEngine.getVideoEngine().getVideoState();
            if (mVideoState != null) {
                long positionInMS = (AVEngine.getVideoEngine().getMainClock() + 999) / 1000;
                long durationInMS = (mVideoState.durationUS + 999) / 1000;
                mTimeInfo.setText(String.format("%02d:%02d:%03d / %02d:%02d:%03d",
                        positionInMS / 1000 / 60 % 60, positionInMS / 1000 % 60, positionInMS % 1000,
                        durationInMS / 1000 / 60 % 60, durationInMS / 1000 % 60, durationInMS % 1000));
                mPlayBtn.setImageResource(mVideoState.status == START ? R.drawable.icon_video_pause : R.drawable.icon_video_play);
                if (mVideoState.status == START) {
                    int correctScrollX = (int) ((THUMB_SLOT_WIDTH * getResources().getDisplayMetrics().density) / 1000000.f * AVEngine.getVideoEngine().getMainClock());
//                    mThumbDragRecyclerView.smoothScrollBy(correctScrollX - mScrollX, 0);
                }
            }
        });
    }


}