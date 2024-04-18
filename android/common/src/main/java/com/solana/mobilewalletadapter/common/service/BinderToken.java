package com.solana.mobilewalletadapter.common.service;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class BinderToken implements Parcelable {
    private final IBinder mBinder;

    public BinderToken() {
        mBinder = new Binder();
    }

    protected BinderToken(Parcel in) {
        mBinder = in.readStrongBinder();
    }

    public IBinder getBinder() {
        return mBinder;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeStrongBinder(mBinder);
    }

    public static final Creator<BinderToken> CREATOR = new Creator<BinderToken>() {
        @Override
        public BinderToken createFromParcel(Parcel in) {
            return new BinderToken(in);
        }

        @Override
        public BinderToken[] newArray(int size) {
            return new BinderToken[size];
        }
    };
}