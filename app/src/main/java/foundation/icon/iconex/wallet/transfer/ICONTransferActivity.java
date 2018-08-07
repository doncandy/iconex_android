package foundation.icon.iconex.wallet.transfer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;

import foundation.icon.iconex.ICONexApp;
import foundation.icon.iconex.MyConstants;
import foundation.icon.iconex.R;
import foundation.icon.iconex.barcode.BarcodeCaptureActivity;
import foundation.icon.iconex.control.OnKeyPreImeListener;
import foundation.icon.iconex.control.WalletEntry;
import foundation.icon.iconex.control.WalletInfo;
import foundation.icon.iconex.dialogs.SendConfirmDialog;
import foundation.icon.iconex.realm.RealmUtil;
import foundation.icon.iconex.service.NetworkService;
import foundation.icon.iconex.util.ConvertUtil;
import foundation.icon.iconex.wallet.contacts.ContactsActivity;
import foundation.icon.iconex.wallet.transfer.data.TxInfo;
import foundation.icon.iconex.widgets.MyEditText;
import loopchain.icon.wallet.core.Constants;
import loopchain.icon.wallet.service.crypto.PKIUtils;

public class ICONTransferActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = ICONTransferActivity.class.getSimpleName();

    private Button btnBack;
    private MyEditText editSend, editAddress;
    private View lineSend, lineAddress;
    private TextView txtSendWarning, txtAddrWarning;
    private Button btnDelAmount, btnDelAddr;
    private TextView txtTransSend;
    private Button btnPlus10, btnPlus100, btnPlus1000, btnTheWhole;
    private Button btnContacts, btnScan;

    private TextView txtFee, txtTransFee;
    private TextView txtRemain, txtTransRemain;

    private Button btnSend;

    private String balance;
    private BigInteger icx;

    private final String CODE_EXCHANGE = "icxusd";
    private String EXCHANGE_PRICE = "";
    private static final String HEX_FEE = "0x2386f26fc10000";
    private String FEE;
    private static final String NONCE = "8367273";
    private String timestamp = null;
    private String txHash = null;

    private WalletInfo mWalletInfo;
    private WalletEntry mWalletEntry;
    private String privKey;

    private NetworkService mService;
    private boolean mBound = false;

    private static final int RC_CONTACTS = 9001;
    private static final int RC_BARCODE_CAPTURE = 9002;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            NetworkService.NetworkServiceBinder binder = (NetworkService.NetworkServiceBinder) service;
            mService = binder.getService();
            mService.registerExchangeCallback(mExchangeCallback);
            mService.registerRemCallback(mRemittanceCallback);

            if (mBound) {
                mService.requestExchangeList(CODE_EXCHANGE);
            } else {
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    private NetworkService.ExchangeCallback mExchangeCallback = new NetworkService.ExchangeCallback() {
        @Override
        public void onReceiveExchangeList() {
            for (Map.Entry<String, String> entry : ICONexApp.EXCHANGE_TABLE.entrySet()) {
                if (entry.getKey().equals(CODE_EXCHANGE))
                    EXCHANGE_PRICE = entry.getValue();
            }
        }

        @Override
        public void onReceiveError(String resCode) {
        }

        @Override
        public void onReceiveException(Throwable t) {
        }
    };

    private OnKeyPreImeListener onKeyPreImeListener = new OnKeyPreImeListener() {
        @Override
        public void onBackPressed() {
            boolean result = validateSendAmount(editSend.getText().toString())
                    && validateAddress(editAddress.getText().toString());
            if (result) {
                btnSend.setEnabled(true);
            } else {
                btnSend.setEnabled(false);
            }
        }
    };

    private NetworkService.RemittanceCallback mRemittanceCallback = new NetworkService.RemittanceCallback() {
        @Override
        public void onReceiveTransactionResult(String id, String txHash) {

            String contactName = findContactName(editAddress.getText().toString());
            if (contactName == null)
                contactName = "";

            RealmUtil.addRecentSend(RealmUtil.COIN_TYPE.ICX, txHash, contactName,
                    editAddress.getText().toString(), timestamp, editSend.getText().toString(), mWalletEntry.getSymbol());
            RealmUtil.loadRecents();

            icx = icx.subtract(ConvertUtil.valueToBigInteger(editSend.getText().toString(), 18));
            mWalletEntry.setBalance(ConvertUtil.getValue(icx, 18));
            ((TextView) findViewById(R.id.txt_balance)).setText(ConvertUtil.getValue(icx, 18));
            ((TextView) findViewById(R.id.txt_fee)).setText(FEE);
            String strPrice = ICONexApp.EXCHANGE_TABLE.get(CODE_EXCHANGE);
            if (strPrice != null) {
                Double balanceUSD = Double.parseDouble(ConvertUtil.getValue(icx, 18))
                        * Double.parseDouble(strPrice);

                String strBalanceUSD = String.format(Locale.getDefault(), "%,.2f", balanceUSD);
                ((TextView) findViewById(R.id.txt_trans_balance))
                        .setText(String.format(getString(R.string.exchange_usd), strBalanceUSD));
            }

            Toast.makeText(getApplicationContext(), getString(R.string.msgDoneRequestTransfer), Toast.LENGTH_SHORT).show();

            finish();
        }

        @Override
        public void onReceiveError(String address, int code) {
        }

        @Override
        public void onReceiveException(Throwable t) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_transfer);

        if (getIntent() != null) {
            mWalletInfo = (WalletInfo) getIntent().getExtras().get("walletInfo");
            mWalletEntry = (WalletEntry) getIntent().getExtras().get("walletEntry");
            privKey = getIntent().getStringExtra("privateKey");
        }

        EXCHANGE_PRICE = ICONexApp.EXCHANGE_TABLE.get(CODE_EXCHANGE);

        ((TextView) findViewById(R.id.txt_title)).setText(mWalletInfo.getAlias());
        ((TextView) findViewById(R.id.txt_possession))
                .setText(String.format(getString(R.string.possessionAmount), mWalletEntry.getSymbol()));
        ((TextView) findViewById(R.id.txt_send_amount))
                .setText(String.format(getString(R.string.sendAmount), mWalletEntry.getSymbol()));
        ((TextView) findViewById(R.id.txt_send_fee))
                .setText(String.format(getString(R.string.sendFee), mWalletEntry.getSymbol()));
        ((TextView) findViewById(R.id.txt_remain_amount))
                .setText(String.format(getString(R.string.remainBalance), mWalletEntry.getSymbol()));
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(this);


        lineSend = findViewById(R.id.line_send_amount);
        lineAddress = findViewById(R.id.line_to_address);

        txtSendWarning = findViewById(R.id.txt_send_warning);
        txtAddrWarning = findViewById(R.id.txt_address_warning);

        editSend = findViewById(R.id.edit_send_amount);
        editSend.setOnKeyPreImeListener(onKeyPreImeListener);
        editSend.setLongClickable(false);
        editSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        editSend.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    lineSend.setBackgroundColor(getResources().getColor(R.color.editActivated));
                } else {
                    lineSend.setBackgroundColor(getResources().getColor(R.color.editNormal));
                    if (editSend.getText().toString().length() > 0) {
                        validateSendAmount(editSend.getText().toString());
                    }
                }
            }
        });
        editSend.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    btnDelAmount.setVisibility(View.VISIBLE);
                    String amount;

                    if (s.toString().startsWith(".")) {
                        editSend.setText("");
                    } else {

                        if (s.toString().indexOf(".") < 0) {
                            if (s.length() > 10) {
                                editSend.setText(s.subSequence(0, 10));
                                editSend.setSelection(10);
                            }
                        } else {
                            String[] values = s.toString().split("\\.");

                            if (values.length == 2) {
                                String decimal = values[0];
                                String below = values[1];

                                if (decimal.length() > 10) {
                                    decimal = decimal.substring(0, 10);
                                    editSend.setText(decimal + "." + below);
                                    editSend.setSelection(editSend.getText().toString().length());
                                } else if (below.length() > 18) {
                                    below = below.substring(0, 18);
                                    editSend.setText(decimal + "." + below);
                                    editSend.setSelection(editSend.getText().toString().length());
                                }
                            }
                        }

                        amount = editSend.getText().toString();
                        String strPrice = ICONexApp.EXCHANGE_TABLE.get(CODE_EXCHANGE);
                        if (strPrice != null) {
                            Double transUSD = Double.parseDouble(amount)
                                    * Double.parseDouble(strPrice);
                            String strTransUSD = String.format("%,.2f", transUSD);

                            txtTransSend.setText(String.format("%s USD", strTransUSD));
                        }
                        setRemain(amount);
                    }
                } else {
                    btnDelAmount.setVisibility(View.INVISIBLE);
                    txtTransSend.setText(String.format("%s USD", MyConstants.NO_BALANCE));
                    btnSend.setEnabled(false);

                    txtSendWarning.setVisibility(View.GONE);
                    if (editSend.isFocused())
                        lineSend.setBackgroundColor(getResources().getColor(R.color.editActivated));
                    else
                        lineSend.setBackgroundColor(getResources().getColor(R.color.editNormal));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        editSend.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                    editAddress.requestFocus();
                }
                return false;
            }
        });

        btnDelAmount = findViewById(R.id.del_amount);
        btnDelAmount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editSend.setText("");
            }
        });

        editAddress = findViewById(R.id.edit_to_address);
        editAddress.setOnKeyPreImeListener(onKeyPreImeListener);
        editAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        editAddress.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    lineAddress.setBackgroundColor(getResources().getColor(R.color.editActivated));
                } else {
                    lineAddress.setBackgroundColor(getResources().getColor(R.color.editNormal));
                    if (editAddress.getText().toString().length() > 0)
                        validateAddress(editAddress.getText().toString());
                }
            }
        });
        editAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    btnDelAddr.setVisibility(View.VISIBLE);
                    if (s.toString().startsWith("hx")) {
                        String tmp = s.toString().substring(2);
                        if (tmp.length() != 40)
                            btnSend.setEnabled(false);
                        else
                            setSendEnable();
                    } else {
                        btnSend.setEnabled(false);
                    }
                } else {
                    btnDelAddr.setVisibility(View.INVISIBLE);
                    btnSend.setEnabled(false);

                    txtAddrWarning.setVisibility(View.GONE);
                    if (editAddress.isFocused())
                        lineAddress.setBackgroundColor(getResources().getColor(R.color.editActivated));
                    else
                        lineAddress.setBackgroundColor(getResources().getColor(R.color.editNormal));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        editAddress.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    setSendEnable();
                }
                return false;
            }
        });

        btnDelAddr = findViewById(R.id.del_address);
        btnDelAddr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editAddress.setText("");
            }
        });

        txtRemain = findViewById(R.id.txt_remain);
        txtTransRemain = findViewById(R.id.txt_trans_remain);
        txtTransSend = findViewById(R.id.txt_trans_send_amount);

        btnPlus10 = findViewById(R.id.btn_plus_10);
        btnPlus10.setOnClickListener(this);
        btnPlus100 = findViewById(R.id.btn_plus_100);
        btnPlus100.setOnClickListener(this);
        btnPlus1000 = findViewById(R.id.btn_plus_1000);
        btnPlus1000.setOnClickListener(this);
        btnTheWhole = findViewById(R.id.btn_plus_all);
        btnTheWhole.setOnClickListener(this);

        btnContacts = findViewById(R.id.btn_contacts);
        btnContacts.setOnClickListener(this);
        btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(this);

        btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(this);

        FEE = ConvertUtil.getValue(ConvertUtil.hexStringToBigInt(HEX_FEE, 18), 18);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Intent intent = new Intent(this, NetworkService.class);
        mBound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        icx = new BigInteger(mWalletEntry.getBalance());

        ((TextView) findViewById(R.id.txt_balance)).setText(ConvertUtil.getValue(icx, 18));
        ((TextView) findViewById(R.id.txt_fee)).setText(Double.toString(new BigDecimal(FEE).stripTrailingZeros().doubleValue()));
        String strPrice = ICONexApp.EXCHANGE_TABLE.get(CODE_EXCHANGE);
        if (strPrice != null) {
            Double balanceUSD = Double.parseDouble(ConvertUtil.getValue(icx, 18))
                    * Double.parseDouble(strPrice);

            String strBalanceUSD = String.format(Locale.getDefault(), "%,.2f", balanceUSD);
            ((TextView) findViewById(R.id.txt_trans_balance))
                    .setText(String.format(getString(R.string.exchange_usd), strBalanceUSD));

            Double feeUSD = Double.parseDouble(FEE)
                    * Double.parseDouble(strPrice);
            String strFeeUSD = String.format(Locale.getDefault(), "%,.2f", feeUSD);
            ((TextView) findViewById(R.id.txt_trans_fee))
                    .setText(String.format(getString(R.string.exchange_usd), strFeeUSD));

            setRemain(editSend.getText().toString());
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_back:
                finish();
                break;
            case R.id.btn_plus_10:
                addPlus(10);
                setSendEnable();
                editSend.setSelection(editSend.getText().toString().length());
                break;

            case R.id.btn_plus_100:
                addPlus(100);
                setSendEnable();
                editSend.setSelection(editSend.getText().toString().length());
                break;

            case R.id.btn_plus_1000:
                addPlus(1000);
                setSendEnable();
                editSend.setSelection(editSend.getText().toString().length());
                break;

            case R.id.btn_plus_all:
                if (icx.compareTo(ConvertUtil.valueToBigInteger(FEE, 18)) < 0) {
                    editSend.setText("");
                    lineSend.setBackgroundColor(getResources().getColor(R.color.colorWarning));
                    txtSendWarning.setVisibility(View.VISIBLE);
                    txtSendWarning.setText(getString(R.string.errNeedFee));
                } else {
                    BigInteger allIcx = icx.subtract(ConvertUtil.valueToBigInteger(FEE, 18));
                    editSend.setText(ConvertUtil.getValue(allIcx, 18));
                    setSendEnable();
                }

                editSend.setSelection(editSend.getText().toString().length());
                break;

            case R.id.btn_contacts:
                startActivityForResult(new Intent(this, ContactsActivity.class)
                        .putExtra("coinType", mWalletInfo.getCoinType())
                        .putExtra("address", mWalletInfo.getAddress()), RC_CONTACTS);
                break;

            case R.id.btn_scan:
                Intent intent = new Intent(this, BarcodeCaptureActivity.class);
                intent.putExtra(BarcodeCaptureActivity.AutoFocus, true);
                intent.putExtra(BarcodeCaptureActivity.UseFlash, false);

                startActivityForResult(intent, RC_BARCODE_CAPTURE);
                break;

            case R.id.btn_send:
                BigInteger value = ConvertUtil.valueToBigInteger(editSend.getText().toString(), 18);
                TxInfo txInfo = new TxInfo(ConvertUtil.getValue(value, 18), Double.toString(Double.parseDouble(FEE)),
                        editAddress.getText().toString());

                SendConfirmDialog dialog = new SendConfirmDialog(this, txInfo);
                dialog.setOnDialogListener(new SendConfirmDialog.OnDialogListener() {
                    @Override
                    public void onOk() {
                        timestamp = getTimeStamp();
                        mService.requestICXTransaction(mWalletEntry.getId(), timestamp, mWalletInfo.getAddress(),
                                editAddress.getText().toString(), editSend.getText().toString(),
                                HEX_FEE, privKey);
                    }
                });
                dialog.show();
                break;
        }
    }

    private void setSendEnable() {
        boolean result = validateSendAmount(editSend.getText().toString())
                && validateAddress(editAddress.getText().toString());
        if (result) {
            btnSend.setEnabled(true);
        } else {
            btnSend.setEnabled(false);
        }
    }

    private void setRemain(String value) {
        BigInteger bigFee = ConvertUtil.valueToBigInteger(FEE, 18);
        BigInteger bigRemain = null;
        BigInteger bigSend;

        String strPrice = ICONexApp.EXCHANGE_TABLE.get(CODE_EXCHANGE);

        boolean isNegative = false;

        if (editSend.getText().toString().isEmpty()) {

            if (icx.compareTo(bigFee) < 0) {
                bigRemain = bigFee.subtract(icx);
                isNegative = true;
            } else {
                bigRemain = icx.subtract(bigFee);
                isNegative = false;
            }
        } else {
            bigSend = ConvertUtil.valueToBigInteger(value, 18);
            switch (icx.compareTo(bigSend)) {
                case -1:
                    bigRemain = (bigSend.add(bigFee)).subtract(icx);
                    isNegative = true;
                    break;
                case 0:
                    bigRemain = bigFee;
                    isNegative = true;
                    break;
                case 1:
                    BigInteger realBigSend = bigSend.add(bigFee);
                    if (icx.compareTo(realBigSend) < 0) {
                        bigRemain = realBigSend.subtract(icx);
                        isNegative = true;
                    } else {
                        bigRemain = icx.subtract(realBigSend);
                        isNegative = false;
                    }
                    break;
            }
        }

        if (strPrice != null) {
            Double remainUSD = Double.parseDouble(ConvertUtil.getValue(bigRemain, 18))
                    * Double.parseDouble(strPrice);
            String strRemainUSD = String.format(Locale.getDefault(), "%,.2f", remainUSD);

            if (isNegative) {
                txtRemain.setText(String.format(getString(R.string.txWithdraw), ConvertUtil.getValue(bigRemain, 18)));
                txtTransRemain.setText(String.format(getString(R.string.exchange_usd), String.format(getString(R.string.txWithdraw), strRemainUSD)));
            } else {
                txtRemain.setText(ConvertUtil.getValue(bigRemain, 18));
                txtTransRemain.setText(String.format(getString(R.string.exchange_usd), strRemainUSD));
            }
        }
    }

    private void addPlus(int plus) {
        String value;
        if (editSend.getText().toString().isEmpty()) {
            editSend.setText(Integer.toString(plus));
        } else {
            value = editSend.getText().toString();
            if (value.indexOf(".") < 0) {
                value = Integer.toString(Integer.parseInt(value) + plus);
                editSend.setText(value);
            } else {
                String[] total = value.split("\\.");
                total[0] = Integer.toString(Integer.parseInt(total[0]) + plus);
                editSend.setText(total[0] + "." + total[1]);
            }
        }
    }

    private boolean validateSendAmount(String value) {
        if (value.isEmpty()) {
            txtSendWarning.setVisibility(View.GONE);
            return false;
        }

        BigInteger sendAmount = ConvertUtil.valueToBigInteger(value, 18);
        BigInteger canICX = icx.subtract(ConvertUtil.valueToBigInteger(FEE, 18));
        if (sendAmount.equals(BigInteger.ZERO)) {
            lineSend.setBackgroundColor(getResources().getColor(R.color.colorWarning));
            txtSendWarning.setVisibility(View.VISIBLE);
            txtSendWarning.setText(getString(R.string.errNonZero));

            return false;
        } else if (icx.compareTo(sendAmount) < 0) {
            lineSend.setBackgroundColor(getResources().getColor(R.color.colorWarning));
            txtSendWarning.setVisibility(View.VISIBLE);
            txtSendWarning.setText(getString(R.string.errNotEnough));

            return false;
        } else if (canICX.compareTo(sendAmount) < 0) {
            lineSend.setBackgroundColor(getResources().getColor(R.color.colorWarning));
            txtSendWarning.setVisibility(View.VISIBLE);
            txtSendWarning.setText(getString(R.string.errNeedFee));

            return false;
        }

        if (editSend.hasFocus())
            lineSend.setBackgroundColor(getResources().getColor(R.color.editActivated));
        else
            lineSend.setBackgroundColor(getResources().getColor(R.color.editNormal));
        editSend.setSelection(editSend.getText().toString().length());
        txtSendWarning.setVisibility(View.GONE);
        return true;
    }

    private boolean validateAddress(String address) {
        if (address.isEmpty()) {
            return false;
        }

        if (address.equals(mWalletInfo.getAddress())) {
            lineAddress.setBackgroundColor(getResources().getColor(R.color.colorWarning));
            txtAddrWarning.setVisibility(View.VISIBLE);
            txtAddrWarning.setText(getString(R.string.errSameAddress));
            return false;
        }

        if (address.startsWith("hx")) {
            address = address.substring(2);
            if (address.length() != 40) {
                lineAddress.setBackgroundColor(getResources().getColor(R.color.colorWarning));
                txtAddrWarning.setVisibility(View.VISIBLE);
                txtAddrWarning.setText(getString(R.string.errCheckAddress));

                return false;
            }
        } else if (address.contains(" ")) {
            lineAddress.setBackgroundColor(getResources().getColor(R.color.colorWarning));
            txtAddrWarning.setVisibility(View.VISIBLE);
            txtAddrWarning.setText(getString(R.string.errCheckAddress));

            return false;
        } else {
            lineAddress.setBackgroundColor(getResources().getColor(R.color.colorWarning));
            txtAddrWarning.setVisibility(View.VISIBLE);
            txtAddrWarning.setText(getString(R.string.errCheckAddress));

            return false;
        }

        if (editAddress.hasFocus())
            lineAddress.setBackgroundColor(getResources().getColor(R.color.editActivated));
        else
            lineAddress.setBackgroundColor(getResources().getColor(R.color.editNormal));
        editAddress.setSelection(editAddress.getText().toString().length());
        txtAddrWarning.setVisibility(View.GONE);
        return true;
    }

    private byte[] makeTbs(String fee, String from, String timestamp, String to, String value, String nonce) {
        String tbs = Constants.METHOD_SENDTRANSACTION + ".fee." + fee + ".from." + from;
        if (nonce != null)
            tbs = tbs + ".nonce." + nonce;
        tbs = tbs + ".timestamp." + timestamp + ".to." + to + ".value." + value;
        return tbs.getBytes();
    }

    public String getTxHash(byte[] _tbs) {
        try {
            byte[] hash = PKIUtils.hash(_tbs, PKIUtils.ALGORITHM_HASH);
            return PKIUtils.hexEncode(hash);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected String getTimeStamp() {
        long time = System.currentTimeMillis() * 1000;
        return Long.toString(time);
    }

    private String findContactName(String address) {
        for (int i = 0; i < ICONexApp.mWallets.size(); i++) {
            if (address.equals(ICONexApp.mWallets.get(i).getAddress()))
                return ICONexApp.mWallets.get(i).getAlias();
        }

        for (int j = 0; j < ICONexApp.ICXContacts.size(); j++) {
            if (address.equals(ICONexApp.ICXContacts.get(j).getAddress()))
                return ICONexApp.ICXContacts.get(j).getName();
        }

        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_CONTACTS) {
            if (resultCode == ContactsActivity.CODE_RESULT) {
                String address = data.getStringExtra("address");
                editAddress.setText(address);

                boolean result = validateSendAmount(editSend.getText().toString())
                        && validateAddress(editAddress.getText().toString());
                btnSend.setEnabled(result);
            }
        } else if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    editAddress.setText(barcode.displayValue);
                    boolean result = validateSendAmount(editSend.getText().toString())
                            && validateAddress(editAddress.getText().toString());
                    btnSend.setEnabled(result);
                } else {
                    Log.d(TAG, "No barcode captured, intent data is null");
                }
            } else {

            }
        }
    }
}