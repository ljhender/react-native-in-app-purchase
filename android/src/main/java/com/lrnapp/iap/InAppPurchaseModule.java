package com.lrnapp.iap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.LifecycleEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class InAppPurchaseModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private static final int REQUEST_CODE_PURCHASE = 149455;

    private static final String ITEM_ID_LIST = "ITEM_ID_LIST";
    private static final String INAPP = "inapp";
    private static final String RESPONSE_CODE = "RESPONSE_CODE";
    private static final String DETAILS_LIST = "DETAILS_LIST";
    private static final String BUY_INTENT = "BUY_INTENT";
    private static final String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    private static final String INAPP_DATA_SIGNATURE = "INAPP_DATA_SIGNATURE";
    private static final String INAPP_PURCHASE_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    private static final String INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    private static final String INAPP_DATA_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    private static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    private static final int BILLING_API_VERSION = 3;
    private static final int BILLING_RESPONSE_RESULT_OK = 0;

    // prop values from: http://developer.android.com/google/play/billing/billing_reference.html
    // on 12/22/2015
    private static final String PROP_PRODUCT_ID = "productId";

    private static final String PROP_AUTO_RENEWING = "autoRenewing";
    private static final String PROP_ORDER_ID = "orderId";
    private static final String PROP_PACKAGE_NAME = "packageName";
    private static final String PROP_PURCHASE_TIME = "purchaseTime";
    // valid purchase states: 0 (purchased), 1 (canceled), or 2 (refunded)
    private static final String PROP_PURCHASE_STATE = "purchaseState";
    private static final String PROP_PURCHASE_TOKEN = "purchaseToken";
    private static final String PROP_DEVELOPER_PAYLOAD = "developerPayload";

    // expected data returned from getSkuDetails() method
    private static final String PROP_TYPE = "type";
    private static final String PROP_PRICE = "price";
    private static final String PROP_PRICE_AMOUNT_MICROS = "price_amount_micros";
    private static final String PROP_PRICE_CURRENCY_CODE = "price_currency_code";
    private static final String PROP_TITLE = "title";
    private static final String PROP_DESCRIPTION = "description";

    // Wrapper key
    private static final String PROP_DATA = "data";
    private static final String PROP_SIGNATURE = "signature";


    private static final String ERROR_PRODUCTS_LOAD_FAILED = "Failed to load products";
    private static final String ERROR_PURCHASE_VERIFICATION_FAILED = "Failed to verify purchase";
    private static final String ERROR_PURCHASE_CANCELLED = "Purchase was cancelled";
    private static final String ERROR_PURCHASE_UNKNOWN = "An error occurred while purchase";


    private static final class Purchase {
        private String mProductId;
        private String mToken;
        private Promise mPromise;

        Purchase(final String productId, final String token, final Promise promise) {
            mProductId = productId;
            mToken = token;
            mPromise = promise;
        }

        public String getProductId() {
            return mProductId;
        }

        public String getToken() {
            return mToken;
        }

        public Promise getPromise() {
            return mPromise;
        }
    }

    private List<Purchase> queuedPurchases = new ArrayList<>();

    private Purchase pendingPurchase;
    private Context mActivityContext;
    private IInAppBillingService mService;
    private boolean init = false;

    // Establish a connection with Billing Service on Google Play
    private ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
        }
    };

    // public InAppPurchaseModule(ReactApplicationContext reactContext, Context activityContext) {
    public InAppPurchaseModule(ReactApplicationContext reactContext) {
        super(reactContext);

        reactContext.addLifecycleEventListener(this);

        // mActivityContext = activityContext;

        // Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");

        // // Explicitly set the intent's target package name to protect the security of billing transactions
        // serviceIntent.setPackage("com.android.vending");
        // activityContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
    }
    // Lifecycle changes
    @Override
    public void onHostResume() {
        if(!init) {
            mActivityContext = this.getCurrentActivity();

            Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");

            // Explicitly set the intent's target package name to protect the security of billing transactions
            serviceIntent.setPackage("com.android.vending");
            activityContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
            init = true;
        }
    }
    @Override
    public void onHostPause() {
    }
    @Override
    public void onHostDestroy() {
    }

    @Override
    public String getName() {
        return "InAppPurchaseModule";
    }

    private WritableMap convertDataToMap(final String data) throws JSONException {
        JSONObject object = new JSONObject(data);

        WritableMap map = Arguments.createMap();
        map.putString(PROP_PRODUCT_ID, object.getString(PROP_PRODUCT_ID));

        // fields specific to getSkuDetails()
        if (object.has(PROP_TYPE))
            map.putString(PROP_TYPE, object.getString(PROP_TYPE));
        if (object.has(PROP_PRICE))
            map.putString(PROP_PRICE, object.getString(PROP_PRICE));
        if (object.has(PROP_PRICE_AMOUNT_MICROS))
            map.putInt(PROP_PRICE_AMOUNT_MICROS, object.getInt(PROP_PRICE_AMOUNT_MICROS));
        if (object.has(PROP_PRICE_CURRENCY_CODE))
            map.putString(PROP_PRICE_CURRENCY_CODE, object.getString(PROP_PRICE_CURRENCY_CODE));
        if (object.has(PROP_TITLE))
            map.putString(PROP_TITLE, object.getString(PROP_TITLE));
        if (object.has(PROP_DESCRIPTION))
            map.putString(PROP_DESCRIPTION, object.getString(PROP_DESCRIPTION));

        // fields specific to get purchases & after a purchase
        if (object.has(PROP_AUTO_RENEWING))
            map.putBoolean(PROP_AUTO_RENEWING, object.getBoolean(PROP_AUTO_RENEWING));
        if (object.has(PROP_ORDER_ID))
            map.putString(PROP_ORDER_ID, object.getString(PROP_ORDER_ID));
        if (object.has(PROP_PACKAGE_NAME))
            map.putString(PROP_PACKAGE_NAME, object.getString(PROP_PACKAGE_NAME));
        if (object.has(PROP_PURCHASE_TIME))
            map.putString(PROP_PURCHASE_TIME, object.getString(PROP_PURCHASE_TIME));
        if (object.has(PROP_PURCHASE_STATE))
            map.putString(PROP_PURCHASE_STATE, object.getString(PROP_PURCHASE_STATE));
        if (object.has(PROP_PURCHASE_TOKEN))
            map.putString(PROP_PURCHASE_TOKEN, object.getString(PROP_PURCHASE_TOKEN));
        if (object.has(PROP_DEVELOPER_PAYLOAD))
            map.putString(PROP_DEVELOPER_PAYLOAD, object.getString(PROP_DEVELOPER_PAYLOAD));

        return map;
    }

    private void onPurchaseHandled() {
        pendingPurchase = null;

        // Handle queued purchases if there are any
        if (!queuedPurchases.isEmpty()) {
            handlePendingPurchase(queuedPurchases.remove(queuedPurchases.size() - 1));
        }
    }

    private void onPurchaseError(final String reason) {
        if (pendingPurchase != null) {
            pendingPurchase.getPromise().reject(reason);
            onPurchaseHandled();
        }
    }

    private void onPurchaseSuccess(final String data, final String signature) {
        if (pendingPurchase != null) {
            try {
                // wrap data & signature in 'outer' layer. data needs to be self contained so that
                // it can be verified against the signature
                WritableMap wrapper = Arguments.createMap();

                // manually add purchase signature
                if (signature != null && !signature.isEmpty())
                    wrapper.putString(PROP_SIGNATURE, signature);

                WritableMap details = convertDataToMap(data);
                String token = null;
                if (details.hasKey(PROP_DEVELOPER_PAYLOAD))
                    token = details.getString(PROP_DEVELOPER_PAYLOAD);
                // put details as string to retain order
                wrapper.putString(PROP_DATA, data);

                // check developer token is valid
                if (pendingPurchase.getToken().equals(token)) {
                    // return wrapper, for access to both data and signature
                    pendingPurchase.getPromise().resolve(wrapper);
                    onPurchaseHandled();
                } else {
                    onPurchaseError(ERROR_PURCHASE_VERIFICATION_FAILED);
                }
            } catch (JSONException e) {
                onPurchaseError(e.getMessage());
            }
        }
    }

    @ReactMethod
    public void loadProducts(final ReadableArray products, final Promise promise) {
        // Use a separate thread for the request as it does a network request
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> skuList = new ArrayList<>();

                for (int i = 0; i < products.size(); i++) {
                    skuList.add(products.getString(i));
                }

                Bundle querySkus = new Bundle();
                querySkus.putStringArrayList(ITEM_ID_LIST, skuList);
                try {
                    Bundle skuDetails = mService.getSkuDetails(BILLING_API_VERSION, mActivityContext.getPackageName(), INAPP, querySkus);

                    int response = skuDetails.getInt(RESPONSE_CODE);
                    if (response == BILLING_RESPONSE_RESULT_OK) {
                        ArrayList<String> responseList = skuDetails.getStringArrayList(DETAILS_LIST);
                        if (responseList == null) {
                            promise.reject(ERROR_PRODUCTS_LOAD_FAILED);
                            return;
                        }

                        WritableMap details = Arguments.createMap();
                        for (String thisResponse : responseList) {
                            WritableMap map = convertDataToMap(thisResponse);
                            details.putMap(map.getString(PROP_PRODUCT_ID), map);
                        }
                        promise.resolve(details);
                    } else {
                        promise.reject(ERROR_PRODUCTS_LOAD_FAILED);
                    }
                } catch (JSONException|RemoteException e) {
                    promise.reject(e.getMessage());
                }
            }
        }).start();
    }

    @ReactMethod
    public void loadPurchases(final String token, final Promise promise) {
        try {
            WritableMap purchases = Arguments.createMap();
            Bundle ownedItems = mService.getPurchases(BILLING_API_VERSION, mActivityContext.getPackageName(), INAPP, token);

            int response = ownedItems.getInt(RESPONSE_CODE);

            if (response == 0) {
                purchases.putString(INAPP_CONTINUATION_TOKEN, ownedItems.getString(INAPP_CONTINUATION_TOKEN));

                ArrayList<String> ownedSkus =
                        ownedItems.getStringArrayList(INAPP_PURCHASE_ITEM_LIST);
                ArrayList<String> purchaseDataList =
                        ownedItems.getStringArrayList(INAPP_PURCHASE_DATA_LIST);
                ArrayList<String> signatureList =
                        ownedItems.getStringArrayList(INAPP_DATA_SIGNATURE_LIST);

                WritableArray items = Arguments.createArray();

                for (int i = 0, l = purchaseDataList.size(); i < l; ++i) {
                    WritableMap data = Arguments.createMap();

                    data.putString("data", purchaseDataList.get(i));
                    data.putString("signature", signatureList.get(i));
                    data.putString("item", ownedSkus.get(i));

                    items.pushMap(data);
                }

                purchases.putArray("items", items);
                promise.resolve(purchases);
            }
        } catch (RemoteException e) {
            promise.reject(e.getMessage());
        }
    }

    private void handlePendingPurchase(final Purchase purchase) {
        try {
            Bundle buyIntentBundle = mService.getBuyIntent(
                    BILLING_API_VERSION, mActivityContext.getPackageName(),
                    purchase.getProductId(), INAPP, purchase.getToken());

            PendingIntent pendingIntent = buyIntentBundle.getParcelable(BUY_INTENT);

            pendingPurchase = purchase;

            ((Activity) mActivityContext).startIntentSenderForResult(pendingIntent.getIntentSender(),
                    REQUEST_CODE_PURCHASE, new Intent(), 0, 0, 0);
        } catch (Exception e) {
            purchase.getPromise().reject(e.getMessage());
        }
    }

    @ReactMethod
    public void consumePurchase(final String purchaseToken, final Promise promise) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int response = -1;
                try {
                    response = mService.consumePurchase(BILLING_API_VERSION, mActivityContext.getPackageName(), purchaseToken);
                } catch (Exception e) {
                    promise.reject(e.getMessage());
                }
                if (response != 0)
                    promise.reject("Purchase consume failed with error code: " + response);
                else
                    promise.resolve(null);
            }
        }).start();
    }

    @ReactMethod
    public void purchaseProduct(final String productId, final String token, final Promise promise) {
        Purchase purchase = new Purchase(productId, token, promise);

        // Android provides us "onActivityResult" to handle purchase requests
        // It means that there is no way to detect cancellation and other errors
        // We can workaround that by handling only one purchase at a time
        // We'll add puchases to a queue to process them
        // It looks like  nasty hack, but hey, whatever works
        if (pendingPurchase != null) {
            queuedPurchases.add(purchase);
        } else {
            handlePendingPurchase(purchase);
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PURCHASE) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    String purchaseData = data.getStringExtra(INAPP_PURCHASE_DATA);
                    String purchaseSig = data.getStringExtra(INAPP_DATA_SIGNATURE);
                    onPurchaseSuccess(purchaseData, purchaseSig);
                    break;
                case Activity.RESULT_CANCELED:
                    onPurchaseError(ERROR_PURCHASE_CANCELLED);
                    break;
                default:
                    onPurchaseError(ERROR_PURCHASE_UNKNOWN);
            }
        }

        return false;
    }

    public void unBindService() {
        if (mService != null) {
            // Unbind from the In-app Billing service when we are done
            // Otherwise, the open service connection could cause the device’s performance to degrade
            mActivityContext.unbindService(mServiceConn);
        }
    }
}
