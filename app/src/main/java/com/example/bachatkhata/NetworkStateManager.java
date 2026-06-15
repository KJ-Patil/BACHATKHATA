package com.example.bachatkhata;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class NetworkStateManager {

    private static NetworkStateManager instance;
    private final ConnectivityManager connectivityManager;
    private final MutableLiveData<Boolean> isNetworkAvailable = new MutableLiveData<>(true);

    private NetworkStateManager(Context context) {
        connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        checkCurrentState();
        registerNetworkCallback();
    }

    public static synchronized NetworkStateManager getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkStateManager(context);
        }
        return instance;
    }

    // Expose both getters for full compatibility
    public LiveData<Boolean> getIsOnline() {
        return isNetworkAvailable;
    }

    public LiveData<Boolean> getIsNetworkAvailable() {
        return isNetworkAvailable;
    }

    private void checkCurrentState() {
        try {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            boolean isConnected = capabilities != null &&
                    (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                     (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)));
            isNetworkAvailable.postValue(isConnected);
        } catch (Exception e) {
            isNetworkAvailable.postValue(false);
        }
    }

    private void registerNetworkCallback() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                isNetworkAvailable.postValue(true);
            }

            @Override
            public void onLost(@NonNull Network network) {
                isNetworkAvailable.postValue(false);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                boolean hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                isNetworkAvailable.postValue(hasInternet);
            }
        });
    }
}
