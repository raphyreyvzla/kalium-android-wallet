package com.banano.kaliumwallet.ui.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import com.banano.kaliumwallet.R;
import com.banano.kaliumwallet.bus.CreatePin;
import com.banano.kaliumwallet.bus.PinComplete;
import com.banano.kaliumwallet.bus.RxBus;
import com.banano.kaliumwallet.databinding.FragmentChangeRepBinding;
import com.banano.kaliumwallet.model.Address;
import com.banano.kaliumwallet.model.Credentials;
import com.banano.kaliumwallet.model.KaliumWallet;
import com.banano.kaliumwallet.network.AccountService;
import com.banano.kaliumwallet.network.model.response.ErrorResponse;
import com.banano.kaliumwallet.network.model.response.ProcessResponse;
import com.banano.kaliumwallet.ui.common.ActivityWithComponent;
import com.banano.kaliumwallet.ui.common.BaseDialogFragment;
import com.banano.kaliumwallet.ui.common.SwipeDismissTouchListener;
import com.banano.kaliumwallet.ui.common.UIUtil;
import com.banano.kaliumwallet.util.SharedPreferencesUtil;
import com.github.ajalt.reprint.core.AuthenticationFailureReason;
import com.github.ajalt.reprint.core.Reprint;
import com.hwangjr.rxbus.annotation.Subscribe;

import java.util.HashMap;

import javax.inject.Inject;

import io.realm.Realm;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

/**
 * Change Representative Dialog
 */
public class ChangeRepDialogFragment extends BaseDialogFragment {
    private FragmentChangeRepBinding binding;
    public static String TAG = ChangeRepDialogFragment.class.getSimpleName();
    private AlertDialog fingerprintDialog;
    private Fragment mTargetFragment;
    private Activity mActivity;
    private boolean changeRepTriggered = false;

    @Inject
    SharedPreferencesUtil sharedPreferencesUtil;

    @Inject
    Realm realm;

    @Inject
    AccountService accountService;

    @Inject
    KaliumWallet wallet;

    /**
     * Create new instance of the dialog fragment (handy pattern if any data needs to be passed to it)
     *
     * @return New instance of ChangeRepDialogFragment
     */
    public static ChangeRepDialogFragment newInstance() {
        Bundle args = new Bundle();
        ChangeRepDialogFragment fragment = new ChangeRepDialogFragment();
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
        mActivity = getActivity();
        mTargetFragment = getTargetFragment();
        if (getActivity() instanceof ActivityWithComponent) {
            ((ActivityWithComponent) getActivity()).getActivityComponent().inject(this);
        }
        changeRepTriggered = false;

        // inflate the view
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_change_rep, container, false);
        view = binding.getRoot();

        // subscribe to bus
        RxBus.get().register(this);

        // Restrict height
        Window window = getDialog().getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int height = metrics.heightPixels;
        double heightPercent = 0.95;
        if (metrics.heightPixels > 1500) {
            heightPercent = 0.9;
        } else {
            ViewGroup.MarginLayoutParams topMargin = (ViewGroup.MarginLayoutParams)binding.repFieldContainer.getLayoutParams();
            topMargin.topMargin = (int)UIUtil.convertDpToPixel(25, getContext());
            binding.repFieldContainer.setLayoutParams(topMargin);
            binding.changeRepHeader.setText(getString(R.string.change_representative_header_short));
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
                binding.changeRepContainer.requestFocus();
                hideKeyboard(view);
            }
        }, SwipeDismissTouchListener.TOP_TO_BOTTOM));

        // Set values
        binding.setHandlers(new ClickHandlers());

        // Set current representative
        String representative = wallet.getRepresentativeAccount();
        if (wallet.getOpenBlock() == null) {
            if (sharedPreferencesUtil.getCustomRepresentative() != null) {
                representative = sharedPreferencesUtil.getCustomRepresentative();
            }
        }
        binding.currentRepresentative.setText(UIUtil.getColorizedSpannableBrightWhite(representative, getContext()));

        // Set color on destination  when valid
        binding.newRep.addTextChangedListener(new TextWatcher() {
            String lastText = "";
            boolean isColorized = false;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void afterTextChanged(Editable editable) {  }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String curText = binding.newRep.getText().toString().trim();
                // TODO colorize address on double paste
                if (curText.equals(lastText)) {
                    return;
                } else if (curText.length() > 0 && lastText.length() == 0) {
                    Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "font/overpass_mono_light.ttf");
                    binding.newRep.setPadding(binding.newRep.getPaddingLeft(), binding.newRep.getPaddingTop(), (int)UIUtil.convertDpToPixel(55, getContext()), binding.newRep.getPaddingBottom());
                    binding.newRep.setTypeface(tf);
                } else if (curText.length() == 0 && lastText.length() > 0) {
                    Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "font/nunitosans_extralight.ttf");
                    binding.newRep.setPadding(binding.newRep.getPaddingLeft(), binding.newRep.getPaddingTop(), (int)UIUtil.convertDpToPixel(20, getContext()), binding.newRep.getPaddingBottom());
                    binding.newRep.setTypeface(tf);
                }
                if (!curText.equals(lastText)) {
                    lastText = curText;
                    Address address = new Address(curText);
                    if (address.isValidAddress()) {
                        hideAddressError();
                        isColorized = true;
                        binding.newRep.setText(UIUtil.getColorizedSpannableBrightWhite(address.getAddress(),  getContext()));
                        binding.newRep.setSelection(address.getAddress().length());
                    } else {
                        if (isColorized) {
                            binding.newRep.setText(new SpannableString(curText));
                            binding.newRep.setSelection(curText.length());
                            isColorized = false;
                        }
                    }
                }
            }

        });

        // Hide keyboard in new rep field when return is pushed
        binding.newRep.setImeOptions(EditorInfo.IME_ACTION_DONE);
        binding.newRep.setRawInputType(InputType.TYPE_CLASS_TEXT);

        // Remove hint when focused
        binding.newRep.setOnFocusChangeListener((View view, boolean isFocused) -> {
            if (isFocused) {
                binding.newRep.setHint("");
            } else {
                binding.newRep.setHint(R.string.change_representative_hint);
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // unregister from bus
        if (fingerprintDialog != null) {
            fingerprintDialog.dismiss();
        }
        RxBus.get().unregister(this);
    }

    private void hideAddressError() {
        binding.repAddressValidation.setVisibility(View.INVISIBLE);
    }

    private void showAddressError() {
        binding.repAddressValidation.setVisibility(View.VISIBLE);
    }

    private void showFingerprintDialog(View view) {
        int style = android.os.Build.VERSION.SDK_INT >= 21 ? R.style.AlertDialogCustom : android.R.style.Theme_Holo_Dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), style);
        builder.setMessage(getString(R.string.change_representative_fingerprint));
        builder.setView(view);
        SpannableString negativeText = new SpannableString(getString(android.R.string.cancel));
        negativeText.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, negativeText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setNegativeButton(negativeText, (dialog, which) -> Reprint.cancelAuthentication());

        fingerprintDialog = builder.create();
        fingerprintDialog.setCanceledOnTouchOutside(false);
        // display dialog
        fingerprintDialog.show();
    }

    private void showFingerprintError(AuthenticationFailureReason reason, CharSequence message, View view) {
        if (isAdded()) {
            final HashMap<String, String> customData = new HashMap<>();
            customData.put("description", reason.name());
            TextView textView = view.findViewById(R.id.fingerprint_textview);
            textView.setText(message.toString());
            if (getContext() != null) {
                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.error));
            }
            textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_fingerprint_error, 0, 0, 0);
        }
    }

    private void showLoadingOverlay() {
        if (binding != null && binding.progressOverlay != null) {
            binding.changeRep.setEnabled(false);
            binding.cancelButton.setEnabled(false);
            animateView(binding.progressOverlay, View.VISIBLE, 1, 200);
        }
    }

    private void hideLoadingOverlay() {
        if (binding != null && binding.progressOverlay != null) {
            animateView(binding.progressOverlay, View.GONE, 0, 200);
        }
    }

    private void executeChange(String newRepAddress) {
        changeRepTriggered = true;
        // If account is not open, store in shared prefs to be used on next open
        if (wallet.getOpenBlock() != null) {
            showLoadingOverlay();
            accountService.requestChange(wallet.getFrontierBlock(), wallet.getAccountBalanceBananoRaw().toBigInteger(), newRepAddress);
        } else {
            sharedPreferencesUtil.setCustomRepresentative(binding.newRep.getText().toString());
            if (mTargetFragment != null) {
                mTargetFragment.onActivityResult(getTargetRequestCode(), CHANGE_COMPLETE, mActivity.getIntent());
            }
            dismiss();
        }
    }

    /**
     * Catch errors from the service
     *
     * @param errorResponse Error Response event
     */
    @Subscribe
    public void receiveServiceError(ErrorResponse errorResponse) {
        if (changeRepTriggered) {
            hideLoadingOverlay();
            if (mTargetFragment != null) {
                mTargetFragment.onActivityResult(getTargetRequestCode(), CHANGE_FAILED, mActivity.getIntent());
            }
            dismiss();
        }
    }

    /**
     * Received a successful send response so go back
     *
     * @param processResponse Process Response
     */
    @Subscribe
    public void receiveProcessResponse(ProcessResponse processResponse) {
        if (changeRepTriggered) {
            hideLoadingOverlay();
            if (mTargetFragment != null) {
                mTargetFragment.onActivityResult(getTargetRequestCode(), CHANGE_COMPLETE, mActivity.getIntent());
            }
            dismiss();
            accountService.requestUpdate();
        }
    }

    /**
     * Pin entered correctly
     *
     * @param pinComplete PinComplete object
     */
    @Subscribe
    public void receivePinComplete(PinComplete pinComplete) {
        executeChange(binding.newRep.getText().toString());
    }

    @Subscribe
    public void receiveCreatePin(CreatePin pinComplete) {
        realm.beginTransaction();
        Credentials credentials = realm.where(Credentials.class).findFirst();
        if (credentials != null) {
            credentials.setPin(pinComplete.getPin());
        }
        realm.commitTransaction();
        executeChange(binding.newRep.getText().toString());
    }


    public class ClickHandlers {
        public void onClickClose(View view) {
            dismiss();
        }

        public void onClickInfo(View view) {
            // show the logout are-you-sure dialog
            AlertDialog.Builder builder;
            // Styles
            SpannableString title = new SpannableString(getString(R.string.change_representative_info_header));
            title.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            SpannableString close = new SpannableString(getString(R.string.change_representative_dialog_close));
            close.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, close.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int style = android.os.Build.VERSION.SDK_INT >= 21 ? R.style.AlertDialogCustom : android.R.style.Theme_Holo_Dialog;

            builder = new AlertDialog.Builder(getContext(), style);
            builder.setTitle(title)

                    .setMessage(R.string.change_representative_info)
                    .setNeutralButton(close, (dialog, which) -> {
                    })
                    .show();
        }

        public void onClickChange(View view) {
            Address repAddress = new Address(binding.newRep.getText().toString());
            if (!repAddress.isValidAddress()) {
                showAddressError();
                return;
            }
            Credentials credentials = realm.where(Credentials.class).findFirst();

            if (Reprint.isHardwarePresent() && Reprint.hasFingerprintRegistered()) {
                // show fingerprint dialog
                LayoutInflater factory = LayoutInflater.from(getContext());
                @SuppressLint("InflateParams") final View viewFingerprint = factory.inflate(R.layout.view_fingerprint, null);
                showFingerprintDialog(viewFingerprint);
                com.github.ajalt.reprint.rxjava2.RxReprint.authenticate()
                        .subscribe(result -> {
                            switch (result.status) {
                                case SUCCESS:
                                    fingerprintDialog.hide();
                                    executeChange(binding.newRep.getText().toString());
                                    break;
                                case NONFATAL_FAILURE:
                                    showFingerprintError(result.failureReason, result.errorMessage, viewFingerprint);
                                    break;
                                case FATAL_FAILURE:
                                    showFingerprintError(result.failureReason, result.errorMessage, viewFingerprint);
                                    break;
                            }
                        });
            } else if (credentials != null && credentials.getPin() != null) {
                showPinScreen(getString(R.string.change_representative_pin));
            } else if (credentials != null && credentials.getPin() == null) {
                showCreatePinScreen();
            }
        }

        public void onClickPaste(View view) {
            // paste to rep field
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN)) {
                binding.newRep.setText(clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            }
        }
    }
}
