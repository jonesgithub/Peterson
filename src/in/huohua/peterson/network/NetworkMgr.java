/*******************************************************************************
 * Copyright (c) 2013 Zheng Sun.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Zheng Sun - initial API and implementation
 ******************************************************************************/

package in.huohua.peterson.network;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import in.huohua.peterson.api.AbsApi;
import in.huohua.peterson.api.ApiCallResponse;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

final public class NetworkMgr {
    public interface OnApiCallFinishedListener {
        void onApiCallFinished(ApiCallResponse<?> response);
    }

    private static Handler handler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            if (instance != null) {
                switch (msg.what) {
                case MSG_SYNC_FINISHED:
                    if (msg.obj instanceof ApiCallResponse) {
                        final ApiCallResponse<?> response = (ApiCallResponse<?>) msg.obj;
                        for (final OnApiCallFinishedListener listener : instance.listeners) {
                            listener.onApiCallFinished(response);
                        }
                    }
                    break;
                default:
                    break;
                }
            }
        }
    };

    static private NetworkMgr instance = null;
    static final private int MSG_SYNC_FINISHED = 0;
    static final private String TAG = NetworkMgr.class.getName();

    public static ApiCallResponse<?> doApiCall(final Context context, final AbsApi<?> api) {
        ApiCallResponse<?> response = null;
        try {
            response = api.call(context);
        } catch (final Exception exception) {
            exception.printStackTrace();

            response = api.getEmptyApiCallResponse();
            response.setSucceeded(false);
        }
        return response;
    }

    public static NetworkMgr getInstance() {
        return instance;
    }

    public static void init(final Context context) {
        if (instance == null) {
            instance = new NetworkMgr(context);
        }
    }

    final private Context context;
    final private ThreadPoolExecutor executor;
    final private ConcurrentLinkedQueue<OnApiCallFinishedListener> listeners;

    private NetworkMgr(final Context context) {
        this.context = context;
        this.listeners = new ConcurrentLinkedQueue<OnApiCallFinishedListener>();
        this.executor = new ThreadPoolExecutor(2, 3, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    /*
     * This method has to be called in MAIN thread.
     */
    public void addOnApiCallFinishedListener(final OnApiCallFinishedListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        Log.i(TAG, "addListener");
    }

    private void notifyApiCallFinished(final ApiCallResponse<?> response) {
        final Message msg = handler.obtainMessage(MSG_SYNC_FINISHED, response);
        handler.sendMessage(msg);
    }

    /*
     * This method has to be called in MAIN thread.
     */
    public void removeOnApiCallFinishedListener(final OnApiCallFinishedListener listener) {
        listeners.remove(listener);
        Log.i(TAG, "removeListener (Length=" + listeners.size() + ")");
    }

    public void startSync(final AbsApi<?> api) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final long timeBeforeApiCall = System.currentTimeMillis();
                final ApiCallResponse<?> response = doApiCall(context, api);
                response.setAccessTime(System.currentTimeMillis() - timeBeforeApiCall);
                notifyApiCallFinished(response);
            }
        });
    }
}
