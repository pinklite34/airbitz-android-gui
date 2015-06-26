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

package com.airbitz.fragments.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.airbitz.R;
import com.airbitz.activities.NavigationActivity;
import com.airbitz.adapters.WalletPickerAdapter;
import com.airbitz.api.AirbitzAPI;
import com.airbitz.api.CoreAPI;
import com.airbitz.fragments.BaseFragment;
import com.airbitz.fragments.HelpFragment;
import com.airbitz.fragments.WalletBaseFragment;
import com.airbitz.fragments.settings.SettingFragment;
import com.airbitz.models.Wallet;
import com.airbitz.models.WalletPickerEnum;
import com.airbitz.objects.HighlightOnPressButton;
import com.airbitz.objects.HighlightOnPressImageButton;
import com.airbitz.objects.HighlightOnPressSpinner;
import com.airbitz.objects.QRCamera;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import info.hoang8f.android.segmented.SegmentedGroup;

/**
 * Created on 3/3/14.
 */
public class ImportFragment extends WalletBaseFragment implements
        CoreAPI.OnWalletSweep,
        QRCamera.OnScanResult
{
    public static String URI = "com.airbitz.importfragment.uri";

    private final String TAG = getClass().getSimpleName();
    private Button mAddressButton, mFlashButton, mGalleryButton;
    private NfcAdapter mNfcAdapter;
    private QRCamera mQRCamera;
    private CoreAPI mCoreAPI;
    private View mView;
    private List<Wallet> mWallets;//Actual wallets
    private Handler mHandler = new Handler();
    private NavigationActivity mActivity;
    private LinearLayout mImportLayout;
    private RelativeLayout mBusyLayout;
    private RelativeLayout mCameraLayout;
    private TextView mBusyText;

    private String mTweet, mToken, mMessage, mZeroMessage;
    String mSweptID;
    long mSweptAmount = -1;
    private String mSweptAddress;

    Runnable sweepNotFoundRunner = new Runnable() {
        @Override
        public void run() {
            showBusyLayout(false);
            if(isVisible()) {
                clearSweepAddress();
                mSweptAmount = -1;
                ((NavigationActivity) getActivity()).ShowFadingDialog(getString(R.string.import_wallet_timeout_message));
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCoreAPI = CoreAPI.getApi();
        mActivity = (NavigationActivity) getActivity();
        mWallets = mCoreAPI.getCoreActiveWallets();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (mView == null) {
            mView = inflater.inflate(R.layout.fragment_import_wallet, container, false);
        } else {
            return mView;
        }

        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mCameraLayout = (RelativeLayout) mView.findViewById(R.id.fragment_import_layout_camera);

        mBusyLayout = (RelativeLayout) mView.findViewById(R.id.fragment_import_busy_layout);
        mBusyText = (TextView) mView.findViewById(R.id.fragment_import_busy_text);
        mBusyText.setTypeface(NavigationActivity.latoBlackTypeFace);

        final SegmentedGroup buttons = (SegmentedGroup) mView.findViewById(R.id.import_bottom_buttons);
        mFlashButton = (Button) buttons.findViewById(R.id.fragment_import_button_flash);
        mFlashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttons.clearCheck();
                mQRCamera.setFlashOn(!mQRCamera.isFlashOn());
            }
        });

        mGalleryButton = (Button) buttons.findViewById(R.id.fragment_import_button_photos);
        mGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttons.clearCheck();
                Intent in = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(in, QRCamera.RESULT_LOAD_IMAGE);
            }
        });

        mAddressButton = (Button) buttons.findViewById(R.id.fragment_import_button_address);
        mAddressButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttons.clearCheck();
                showAddressDialog();
            }
        });

        return mView;
    }

    private void showBusyLayout(boolean on) {
        if(on) {
            mBusyLayout.setVisibility(View.VISIBLE);
            stopCamera();
        }
        else {
            mBusyLayout.setVisibility(View.GONE);
            startCamera();
        }
    }

    private void scanQRCodes() {
        mCameraLayout.setVisibility(View.VISIBLE);
        startCamera();

        final NfcManager nfcManager = (NfcManager) mActivity.getSystemService(Context.NFC_SERVICE);
        mNfcAdapter = nfcManager.getDefaultAdapter();

        if (mNfcAdapter != null && mNfcAdapter.isEnabled() && SettingFragment.getNFCPref()) {
//            mQRCodeTextView.setText(getString(R.string.send_scan_text_nfc));
        }
        else {
//            mQRCodeTextView.setText(getString(R.string.send_scan_text));
        }
    }

    private void stopScanningQRCodes() {
        mCameraLayout.setVisibility(View.GONE);
        stopCamera();
    }

    public void stopCamera() {
        Log.d(TAG, "stopCamera");
        if(mQRCamera != null) {
            mQRCamera.stopCamera();
        }
    }

    public void startCamera() {
        if(mQRCamera == null) {
            mQRCamera = new QRCamera(this, mCameraLayout);
            mQRCamera.setOnScanResultListener(this);
        }
        mQRCamera.startCamera();
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle args = getArguments();

        if(args != null && args.getString(URI) != null && getHiddenBitsToken(args.getString(URI)) != null) {
            mSweptAddress = args.getString(URI);
            stopScanningQRCodes();
            showAddressDialog();
        }
        else {
            scanQRCodes();
        }

        mCoreAPI.setOnWalletSweepListener(this);

        clearSweepAddress();
    }

    @Override
    public void onPause() {
        super.onPause();
        showBusyLayout(false);
        stopCamera();
        mHandler.removeCallbacks(sweepNotFoundRunner);
        mCoreAPI.setOnWalletSweepListener(null);
    }

    @Override
    public void onStop() {
        super.onStop();
        stopCamera();
    }

    @Override
    public void onScanResult(String result) {
        if (result != null) {
            Log.d(TAG, "HiddenBits found");
            attemptSubmit(result);
        }
        mQRCamera.setOnScanResultListener(null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mQRCamera != null && requestCode == QRCamera.RESULT_LOAD_IMAGE && resultCode == Activity.RESULT_OK && null != data) {
            mQRCamera.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void attemptSubmit(String uriString) {
        String token = getHiddenBitsToken(uriString);

        String entry = token != null ? token : uriString;

        mBusyText.setText(String.format(getString(R.string.import_wallet_busy_text), entry));
        showBusyLayout(true);
        mSweptAddress = mCoreAPI.SweepKey(mWallet.getUUID(), entry);

        if(mSweptAddress != null && !mSweptAddress.isEmpty()) {
            mHandler.postDelayed(sweepNotFoundRunner, 30000);

            if(token != null) { // also issue hidden bits
                int hBitzIDLength = 4;
                if(mSweptAddress.length() >= hBitzIDLength) {
                    String lastFourChars = mSweptAddress.substring(mSweptAddress.length() - hBitzIDLength, mSweptAddress.length());
                    HiddenBitsApiTask task = new HiddenBitsApiTask();
                    task.execute(lastFourChars);
                }
                else {
                    Log.d(TAG, "HiddenBits token error");
                }
            }
        }
        else {
            showBusyLayout(false);
            mActivity.ShowFadingDialog(getString(R.string.import_wallet_private_key_invalid));
        }
    }

    // Returns null if not a HiddenBits token
    public static String getHiddenBitsToken(String uriIn)
    {
        final String HBITS_SCHEME = "hbits";
        if(uriIn == null)
            return null;

        Uri uri = Uri.parse(uriIn);
        String scheme = uri.getScheme();

        if(scheme != null && scheme.equalsIgnoreCase(HBITS_SCHEME)) {
            Log.d("ImportFragment", "Good HiddenBits URI");
            return uri.toString().substring(scheme.length()+3);
        }
        else {
            Log.d("ImportFragment", "HiddenBits failed for: "+uriIn);
            return null;
        }
    }

    public class HiddenBitsApiTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Getting HiddenBits API response");
        }

        @Override
        protected String doInBackground(String... params) {
            AirbitzAPI api = AirbitzAPI.getApi();
            return api.getHiddenBits(params[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            if(result == null) {
                return;
            }
            Log.d(TAG, "Got HiddenBits API response: " + result);

            JSONObject jsonObject;
            try {
                jsonObject = new JSONObject(result);
                mTweet = jsonObject.getString("tweet");
                mToken = jsonObject.getString("token");
                mZeroMessage = jsonObject.getString("zero_message");
                mMessage = jsonObject.getString("message");

                // Check to see if both paths are done
                checkHiddenBitsAsyncData();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void onCancelled() {
            showBusyLayout(false);
        }
    }

    @Override
    public void OnWalletSweep(String txID, long amount) {
        Log.d(TAG, "OnWalletSweep called with ID:" + txID + " and satoshis:" + amount);

        showBusyLayout(false);

        mSweptID = txID;
        mSweptAmount = amount;

        String token = getHiddenBitsToken(mSweptAddress);
        if(token != null) { // hidden bitz
            // Check to see if both paths are done
            checkHiddenBitsAsyncData();
            return;
        }

        // if a private address sweep
        mHandler.removeCallbacks(sweepNotFoundRunner);

        clearSweepAddress();
        mActivity.showPrivateKeySweepTransaction(mSweptID, mWallet.getUUID(), mSweptAmount);
        mSweptAmount = -1;
    }

    private void clearSweepAddress() {
        // Clear out sweep info
        mSweptAddress = "";
    }

    // This is only called for HiddenBits
    private void checkHiddenBitsAsyncData() {
        // both async paths are finished if both of these are not empty
        if (mSweptAmount != -1 && mTweet != null)
        {
            Log.d(TAG, "Both API and OnWalletSweep are finished");

            mHandler.removeCallbacks(sweepNotFoundRunner);
            showBusyLayout(false);

            mActivity.showHiddenBitsTransaction(mSweptID, mWallet.getUUID(), mSweptAmount,
                    mMessage, mZeroMessage, mTweet);

            mSweptAmount = -1;
        }
    }

    public void showAddressDialog() {
        final EditText editText = new EditText(getActivity());
        if(mSweptAddress != null) {
            editText.setText(mSweptAddress);
        }
        editText.setHint(getResources().getString(R.string.fragment_send_send_to_hint));
        editText.setHintTextColor(getResources().getColor(R.color.text_hint));
        editText.setTextColor(getResources().getColor(R.color.text_dark_gray));
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), R.style.AlertDialogCustom));
        builder.setTitle(getResources().getString(R.string.fragment_import_address_dialog_title))
                .setCancelable(false)
                .setPositiveButton(getResources().getString(R.string.string_done),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                attemptSubmit(editText.getText().toString().trim());
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(Html.fromHtml("<b>" + getResources().getString(R.string.string_cancel) + "</b>"),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
        builder.setView(editText);
        final AlertDialog dialog = builder.create();

        // this changes the colors of the system's UI buttons we're using
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface arg0) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.blue_header_text));
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.blue_header_text));
            }
        });
        dialog.show();
    }
}
