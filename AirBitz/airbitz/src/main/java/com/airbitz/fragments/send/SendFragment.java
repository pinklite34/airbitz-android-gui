/**
 * Copyright (c) 2014, Airbitz Inc
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms are permitted provided that
 * the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Redistribution or use of modified source code requires the express written
 *    permission of Airbitz Inc.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the Airbitz Project.
 */

package com.airbitz.fragments.send;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Html;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;

import com.airbitz.AirbitzApplication;
import com.airbitz.R;
import com.airbitz.activities.NavigationActivity;
import com.airbitz.adapters.BluetoothSearchAdapter;
import com.airbitz.adapters.WalletChoiceAdapter;
import com.airbitz.adapters.WalletOtherAdapter;
import com.airbitz.adapters.WalletPickerAdapter;
import com.airbitz.api.CoreAPI;
import com.airbitz.fragments.BaseFragment;
import com.airbitz.fragments.HelpFragment;
import com.airbitz.fragments.WalletBaseFragment;
import com.airbitz.fragments.settings.SettingFragment;
import com.airbitz.fragments.wallet.WalletsFragment;
import com.airbitz.models.BleDevice;
import com.airbitz.models.Wallet;
import com.airbitz.models.WalletPickerEnum;
import com.airbitz.objects.BleUtil;
import com.airbitz.objects.BluetoothListView;
import com.airbitz.objects.HighlightOnPressButton;
import com.airbitz.objects.HighlightOnPressSpinner;
import com.airbitz.objects.QRCamera;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;

import java.util.ArrayList;
import java.util.List;

public class SendFragment extends WalletBaseFragment implements
        BluetoothListView.OnPeripheralSelected,
        BluetoothListView.OnBitcoinURIReceived,
        QRCamera.OnScanResult
{
    private final String TAG = getClass().getSimpleName();

    private final String FIRST_USAGE_COUNT = "com.airbitz.fragments.send.firstusagecount";
    private final String FIRST_BLE_USAGE_COUNT = "com.airbitz.fragments.send.firstusageblecount";

    public static final String AMOUNT_SATOSHI = "com.airbitz.Sendfragment_AMOUNT_SATOSHI";
    public static final String AMOUNT_FIAT = "com.airbitz.Sendfragment_AMOUNT_FIAT";
    public static final String LABEL = "com.airbitz.Sendfragment_LABEL";
    public static final String CATEGORY = "com.airbitz.Sendfragment_CATEGORY";
    public static final String RETURN_URL = "com.airbitz.Sendfragment_RETURN_URL";
    public static final String NOTES = "com.airbitz.Sendfragment_NOTES";
    public static final String LOCKED = "com.airbitz.Sendfragment_LOCKED";
    public static final String UUID = "com.airbitz.Sendfragment_UUID";
    public static final String IS_UUID = "com.airbitz.Sendfragment_IS_UUID";
    public static final String FROM_WALLET_UUID = "com.airbitz.Sendfragment_FROM_WALLET_UUID";

    private Handler mHandler;
    private boolean hasCheckedFirstUsage;
    private Button mTransferButton;
    private Button mAddressButton;
    private Button mGalleryButton;
    private View mFlashButton;
    private ListView mOtherWalletsListView;
    private RelativeLayout mListviewContainer;
    private RelativeLayout mCameraLayout;
    private RelativeLayout mBluetoothLayout;
    private BluetoothListView mBluetoothListView;
    private List<Wallet> mOtherWalletsList;//NAMES
    private Wallet mFromWallet;
    private String mReturnURL;
    private WalletOtherAdapter mOtherWalletsAdapter;
    private boolean mForcedBluetoothScanning = false;
    private View mView;
    QRCamera mQRCamera;
    private CoreAPI mCoreApi;

    @Override
    protected String getSubtitle() {
        return mActivity.getString(R.string.fragment_send_subtitle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mCoreApi = CoreAPI.getApi();

        mView = inflater.inflate(R.layout.fragment_send, container, false);

        mBluetoothLayout = (RelativeLayout) mView.findViewById(R.id.fragment_send_bluetooth_layout);
        mCameraLayout = (RelativeLayout) mView.findViewById(R.id.fragment_send_layout_camera);
        mQRCamera = new QRCamera(this, mCameraLayout);

        mFlashButton = mView.findViewById(R.id.fragment_send_button_flash);
        mFlashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mQRCamera.setFlashOn(!mQRCamera.isFlashOn());
            }
        });

        mGalleryButton = (Button) mView.findViewById(R.id.fragment_send_button_photos);
        mGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent in = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(in, QRCamera.RESULT_LOAD_IMAGE);
            }
        });

        mTransferButton = (Button) mView.findViewById(R.id.fragment_send_button_transfer);
        mTransferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showOtherWallets();
            }
        });

        mAddressButton = (Button) mView.findViewById(R.id.fragment_send_button_address);
        mAddressButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddressDialog();
            }
        });

        mOtherWalletsList = new ArrayList<Wallet>();
        mOtherWalletsAdapter = new WalletOtherAdapter(getActivity(), mOtherWalletsList);

        mOtherWalletsListView = (ListView) mView.findViewById(R.id.fragment_send_transfer_list);
        View headerView = inflater.inflate(R.layout.fragment_send_other_wallet_layout, null, true);
        ImageButton headerCloseButton = (ImageButton) headerView.findViewById(R.id.fragment_send_header_close_button);
        headerCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideOtherWallets();
            }
        });
        mOtherWalletsListView.addHeaderView(headerView);
        mOtherWalletsListView.setAdapter(mOtherWalletsAdapter);

        mOtherWalletsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                CoreAPI.SpendTarget target = mCoreApi.getNewSpendTarget();
                target.newTransfer(mOtherWalletsList.get(i-1).getUUID());
                GotoSendConfirmation(target);
            }
        });

        // if BLE is supported on the device, enable
        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (SettingFragment.getBLEPref()) {
                mBluetoothListView = new BluetoothListView(mActivity);
                mBluetoothListView.setOnBitcoinURIReceivedListener(this);
                mBluetoothLayout.addView(mBluetoothListView, 0);
            }
            else {
                // Bluetooth is not enabled - ask for enabling?
            }
        }

        return mView;
    }

    // delegated from the containing fragment
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == QRCamera.RESULT_LOAD_IMAGE && resultCode == Activity.RESULT_OK && null != data) {
            mQRCamera.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void checkAndSendAddress(String strTo) {
        newSpend(strTo);
    }

    public void stopCamera() {
        Log.d(TAG, "stopCamera");
        mQRCamera.stopCamera();
    }

    public void startCamera() {
        mQRCamera.startCamera();
        checkFirstUsage();
    }

    private void checkFirstUsage() {
        SharedPreferences prefs = AirbitzApplication.getContext().getSharedPreferences(AirbitzApplication.PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(FIRST_USAGE_COUNT, 1);
        if(count <= 2) {
            count++;
            mActivity.ShowFadingDialog(getString(R.string.fragment_send_first_usage), getResources().getInteger(R.integer.alert_hold_time_help_popups));
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(FIRST_USAGE_COUNT, count);
            editor.apply();
        }
    }

    public void GotoSendConfirmation(CoreAPI.SpendTarget target) {
        if (mFromWallet == null) {
            return;
        }
        SendConfirmationFragment fragment = new SendConfirmationFragment();
        fragment.setSpendTarget(target);
        Bundle bundle = new Bundle();
        bundle.putString(FROM_WALLET_UUID, mFromWallet.getUUID());
        fragment.setArguments(bundle);
        if (mActivity != null)
            mActivity.pushFragment(fragment, NavigationActivity.Tabs.SEND.ordinal());
    }

    @Override
    protected void walletChanged(Wallet newWallet) {
        super.walletChanged(newWallet);

        updateWalletOtherList();
    }

    @Override
    public boolean onBackPress() {
        if (super.onBackPress()) {
            return true;
        }
        return hideOtherWallets();
    }

    @Override
    public void onResume() {
        super.onResume();

        hasCheckedFirstUsage = false;
        if (mHandler == null) {
            mHandler = new Handler();
        }

        startCamera();

        final NfcManager nfcManager = (NfcManager) mActivity.getSystemService(Context.NFC_SERVICE);
        NfcAdapter mNfcAdapter = nfcManager.getDefaultAdapter();

        if (mNfcAdapter != null && mNfcAdapter.isEnabled() && SettingFragment.getNFCPref()) {
//            mQRCodeTextView.setText(getString(R.string.send_scan_text_nfc));
        }
        else {
//            mQRCodeTextView.setText(getString(R.string.send_scan_text));
        }

        mQRCamera.setOnScanResultListener(this);

        checkFirstBLEUsage();
        startBluetoothSearch();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            mQRCamera.startScanning();
        } else {
            if (mQRCamera != null) {
                mQRCamera.stopScanning();
            }
        }
    }

    public void updateWalletOtherList() {
        mOtherWalletsList.clear();
        for (Wallet wallet : mWallets) {
            if (mFromWallet != null && mFromWallet.getUUID() != null && !wallet.getUUID().equals(mFromWallet.getUUID())) {
                mOtherWalletsList.add(wallet);
            }
        }
        mOtherWalletsAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCamera();
        stopBluetoothSearch();
        if(mBluetoothListView != null) {
            mBluetoothListView.close();
        }
        hasCheckedFirstUsage = false;
    }

    @Override
    protected void onAddOptions(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_standard, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (isMenuExpanded()) {
            return super.onOptionsItemSelected(item);
        }
        switch (item.getItemId()) {
            case android.R.id.home:
                return hideOtherWallets();
            case R.id.action_help:
                mActivity.pushFragment(
                        new HelpFragment(HelpFragment.SEND),
                        NavigationActivity.Tabs.SEND.ordinal());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void showMessageAndStartCameraDialog(int title, int message) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(mActivity);
        builder.content(message)
               .title(title)
               .cancelable(false)
               .theme(Theme.LIGHT)
               .neutralText(getResources().getString(R.string.string_ok))
               .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onNeutral(MaterialDialog dialog) {
                        mQRCamera.startScanning();
                        dialog.cancel();
                    }
               });
        builder.show();
    }

    //************** Bluetooth support

    private void checkFirstBLEUsage() {
        if(hasCheckedFirstUsage) {
            return;
        }
        SharedPreferences prefs = AirbitzApplication.getContext().getSharedPreferences(AirbitzApplication.PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(FIRST_BLE_USAGE_COUNT, 1);
        if(count <= 2) {
            count++;
            mActivity.ShowFadingDialog(getString(R.string.fragment_send_first_usage_ble), getResources().getInteger(R.integer.alert_hold_time_help_popups));
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(FIRST_BLE_USAGE_COUNT, count);
            editor.apply();
        }
        hasCheckedFirstUsage = true;
    }

    // Start the Bluetooth search
    private void startBluetoothSearch() {
        if(mBluetoothListView != null && BleUtil.isBleAvailable(mActivity)) {
            mBluetoothListView.setOnPeripheralSelectedListener(this);
            mBluetoothListView.scanForBleDevices(true);
        }
    }

    // Stop the Bluetooth search
    private void stopBluetoothSearch() {
        if(mBluetoothListView != null && BleUtil.isBleAvailable(mActivity)) {
            mBluetoothListView.scanForBleDevices(false);
            mBluetoothListView.close();
        }
    }

    @Override
    public void onPeripheralSelected(BleDevice device) {
        showConnecting(device);
        stopBluetoothSearch();
        mBluetoothListView.setOnPeripheralSelectedListener(null);
        mBluetoothListView.connectGatt(device);
    }

    private MaterialDialog mDialog = null;
    private void showConnecting(BleDevice device) {
        String name = device.getName();
        String msg = String.format(mActivity.getString(R.string.fragment_send_connecting_to_device), name);
        showDialog(msg);
    }

    private void showProcessing() {
        String msg = mActivity.getString(R.string.loading);
        showDialog(msg);
    }

    private void showDialog(String message) {
        if (null != mDialog) {
            mDialog.dismiss();
            mDialog = null;
        }
        MaterialDialog.Builder builder =
            new MaterialDialog.Builder(mActivity)
                    .content(message)
                    .progress(true, 0)
                    .progressIndeterminateStyle(false);
        mDialog = builder.build();
        mDialog.show();
    }

    @Override
    public void onBitcoinURIReceived(final String bitcoinAddress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                checkAndSendAddress(bitcoinAddress);
            }
        });
    }

    @Override
    public void onBitcoinURIMismatch(String response, final String partialAddress, final String partialAdvertisedAddress){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
                String title =
                    mActivity.getString(R.string.bluetoothlistview_address_mismatch_title);
                String message = String.format(getResources().getString(
                    R.string.bluetoothlistview_address_mismatch_message),
                    partialAddress, partialAdvertisedAddress);
                mActivity.ShowOkMessageDialog(title, message);
            }
        });
    }

    @Override
    public void onScanResult(String result) {
        Log.d(TAG, "checking result = " + result);
        if (result != null) {
            showProcessing();
            newSpend(result);
        } else {
            showMessageAndStartCameraDialog(R.string.send_title, R.string.fragment_send_send_bitcoin_unscannable);
        }
    }

    @Override
    public void onWalletsLoaded() {
        super.onWalletsLoaded();
        mFromWallet = mWallet;
        Bundle bundle = getArguments();
        if (bundle != null) {
            if (bundle.getString(WalletsFragment.FROM_SOURCE, "").equals(NavigationActivity.URI_SOURCE)) {
                String uriData = bundle.getString(NavigationActivity.URI_DATA);
                bundle.putString(NavigationActivity.URI_DATA, ""); //to clear the URI_DATA after reading once
                if (!uriData.isEmpty()) {
                    newSpend(uriData);
                }
            }
        }
        updateWalletOtherList();
    }

    private void newSpend(String text) {
        new NewSpendTask().execute(text);
    }

    public class NewSpendTask extends AsyncTask<String, Void, Boolean> {
        CoreAPI.SpendTarget target;

        NewSpendTask() {
            target = mCoreApi.getNewSpendTarget();
        }

        @Override
        protected Boolean doInBackground(String... text) {
            return target.newSpend(text[0]);
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            // If this spend came from BLE
            if (result) {
                GotoSendConfirmation(target);
            } else {
                showMessageAndStartCameraDialog(
                    R.string.fragment_send_failure_title,
                    R.string.fragment_send_confirmation_invalid_bitcoin_address);
            }
            if (null != mDialog) {
                mDialog.dismiss();
                mDialog = null;
            }
        }

        @Override
        protected void onCancelled() {
        }
    }

    public void showAddressDialog() {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.alert_address_form, null);
        final EditText editText = (EditText) view.findViewById(R.id.address);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(mActivity);
        builder.title(getResources().getString(R.string.fragment_send_address_dialog_title))
               .customView(view, false)
               .cancelable(false)
               .positiveText(getResources().getString(R.string.string_done))
               .negativeText(getResources().getString(R.string.string_cancel))
               .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        checkAndSendAddress(editText.getText().toString());
                        dialog.dismiss();
                    }
                    public void onNegative(MaterialDialog dialog) {
                        dialog.cancel();
                    }
                });
        builder.show();
    }

    private boolean toggleOtherWallets() {
        if (mOtherWalletsListView.getVisibility() == View.VISIBLE) {
            return hideOtherWallets();
        } else {
            return showOtherWallets();
        }
    }

    private boolean showOtherWallets() {
        if (mOtherWalletsListView.getVisibility() == View.VISIBLE) {
            return false;
        }

        if (mOtherWalletsList == null || 0 == mOtherWalletsList.size()) {
            mActivity.ShowFadingDialog(
                getString(R.string.fragment_send_create_wallet_to_transfer),
                getResources().getInteger(R.integer.alert_hold_time_help_popups));
            return false;
        }

        ObjectAnimator key =
            ObjectAnimator.ofFloat(mOtherWalletsListView, "translationY", mOtherWalletsListView.getHeight(), 0f);
        key.setDuration(250);
        key.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                finishShowOthers();
            }
        });
        key.start();
        mOtherWalletsListView.setVisibility(View.VISIBLE);
        return true;
    }

    public void finishShowOthers() {
        mOtherWalletsListView.setVisibility(View.VISIBLE);
        mActivity.invalidateOptionsMenu();
        mActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mActivity.getSupportActionBar().setDisplayShowHomeEnabled(true);
    }

    private boolean hideOtherWallets() {
        if (mOtherWalletsListView.getVisibility() == View.INVISIBLE) {
            return false;
        }

        ObjectAnimator key =
            ObjectAnimator.ofFloat(mOtherWalletsListView, "translationY", 0f, mOtherWalletsListView.getHeight());
        key.setDuration(250);
        key.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                finishHideOthers();
            }

            @Override
            public void onAnimationStart(Animator animator) {
                mOtherWalletsListView.setVisibility(View.VISIBLE);
            }
        });
        key.start();
        return true;
    }

    private void finishHideOthers() {
        mOtherWalletsListView.setVisibility(View.INVISIBLE);
        mActivity.invalidateOptionsMenu();
        mExpanded = false;

        if (!mHomeEnabled) {
            mActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            mActivity.getSupportActionBar().setDisplayShowHomeEnabled(false);
        }
    }
}
