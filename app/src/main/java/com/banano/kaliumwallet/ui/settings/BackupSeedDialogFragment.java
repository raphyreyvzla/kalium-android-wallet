package com.banano.kaliumwallet.ui.settings;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.banano.kaliumwallet.R;
import com.banano.kaliumwallet.broadcastreceiver.ClipboardAlarmReceiver;
import com.banano.kaliumwallet.databinding.FragmentBackupSeedBinding;
import com.banano.kaliumwallet.model.Credentials;
import com.banano.kaliumwallet.ui.common.ActivityWithComponent;
import com.banano.kaliumwallet.ui.common.BaseDialogFragment;
import com.banano.kaliumwallet.ui.common.SwipeDismissTouchListener;
import com.banano.kaliumwallet.util.ExceptionHandler;

import javax.inject.Inject;

import io.realm.Realm;

public class BackupSeedDialogFragment extends BaseDialogFragment {
    private FragmentBackupSeedBinding binding;
    public static String TAG = BackupSeedDialogFragment.class.getSimpleName();
    private Handler mHandler;
    private Runnable mRunnable;

    @Inject
    Realm realm;

    /**
     * Create new instance of the dialog fragment (handy pattern if any data needs to be passed to it)
     *
     * @return New instance of BackupSeedDialogFragment
     */
    public static BackupSeedDialogFragment newInstance() {
        Bundle args = new Bundle();
        BackupSeedDialogFragment fragment = new BackupSeedDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.AppTheme_Modal_Window);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // inject
        if (getActivity() instanceof ActivityWithComponent) {
            ((ActivityWithComponent) getActivity()).getActivityComponent().inject(this);
        }

        // No screenshots :smug:
        getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // inflate the view
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_backup_seed, container, false);
        view = binding.getRoot();

        // Restrict height
        Window window = getDialog().getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int height = metrics.heightPixels;
        double heightPercent = 0.95;
        if (metrics.heightPixels > 1500) {
            heightPercent = 0.9;
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, (int) (height * heightPercent));
        window.setGravity(Gravity.BOTTOM);

        // Shadow
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams windowParams = window.getAttributes();
        windowParams.dimAmount = 0.60f;
        windowParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(windowParams);

        // Swipe down to dismiss
        getDialog().getWindow().getDecorView().setOnTouchListener(new SwipeDismissTouchListener(getDialog().getWindow().getDecorView(),
                null, new SwipeDismissTouchListener.DismissCallbacks() {
            @Override
            public boolean canDismiss(Object token) {
                return true;
            }

            @Override
            public void onDismiss(View view, Object token) {
                dismiss();
            }

            @Override
            public void onTap(View view) {
            }
        }, SwipeDismissTouchListener.TOP_TO_BOTTOM));

        // Set values
        binding.setHandlers(new ClickHandlers());

        // get seed from storage
        Credentials credentials = realm.where(Credentials.class).findFirst();
        if (credentials != null) {
            binding.setSeed(credentials.getSeed());
        } else {
            ExceptionHandler.handle(new Exception("Problem accessing generated seed"));
        }

        // Set runnable to reset seed text
        mHandler = new Handler();
        mRunnable = () -> {
            binding.backupSeedSeed.setTextColor(getResources().getColor(R.color.yellow));
            binding.backupSeedCopied.setVisibility(View.INVISIBLE);
        };

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        // Cancel seed copy callback
        if (mHandler != null && mRunnable != null) {
            mHandler.removeCallbacks(mRunnable);
        }
    }

    public class ClickHandlers {
        public void onClickClose(View view) {
            dismiss();
        }

        public void onClickSeed(View v) {
            if (binding != null && binding.backupSeedSeed != null) {
                // copy seed to clipboard
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText(ClipboardAlarmReceiver.CLIPBOARD_NAME, binding.backupSeedSeed.getText().toString());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                }

                binding.backupSeedSeed.setTextColor(getResources().getColor(R.color.green_light));
                binding.backupSeedCopied.setVisibility(View.VISIBLE);

                if (mHandler != null) {
                    mHandler.removeCallbacks(mRunnable);
                    mHandler.postDelayed(mRunnable, 1500);
                }

                setClearClipboardAlarm();
            }
        }
    }
}
