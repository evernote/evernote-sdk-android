/*
 * Copyright 2012 Evernote Corporation
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *  
 * 1. Redistributions of source code must retain the above copyright notice, this 
 *    list of conditions and the following disclaimer.
 *     
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.evernote.client.oauth.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import com.evernote.androidsdk.R;
import com.evernote.client.oauth.EvernoteAuthToken;
import com.evernote.client.oauth.YinxiangApi;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.EvernoteApi;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

/**
 * An Android Activity for authenticating to Evernote using OAuth.
 * Third parties should not need to use this class directly.
 */
public class EvernoteOAuthActivity extends Activity {

  private static final String TAG = "EvernoteOAuthActivity";

  static final String EXTRA_EVERNOTE_HOST = "EVERNOTE_HOST";
  static final String EXTRA_CONSUMER_KEY = "CONSUMER_KEY";
  static final String EXTRA_CONSUMER_SECRET = "CONSUMER_SECRET";
  static final String EXTRA_REQUEST_TOKEN = "REQUEST_TOKEN";
  static final String EXTRA_REQUEST_TOKEN_SECRET = "REQUEST_TOKEN_SECRET";


  private String mEvernoteHost = null;
  private String mConsumerKey = null;
  private String mConsumerSecret = null;
  private String mRequestToken = null;
  private String mRequestTokenSecret = null;

  private Activity mActivity;

  /**
   * Protect member variables in threads
   */
  private static final Object mLock = new Object();

  //Webview
  private WebView mWebView;
  private WebViewClient mWebViewClient = new WebViewClient() {
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      super.onPageStarted(view, url, favicon);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      super.onPageFinished(view, url);
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
      super.onReceivedError(view, errorCode, description, failingUrl);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      Uri uri = Uri.parse(url);
      if (uri.getScheme().equals(getCallbackScheme())) {
        mCompleteAuthentication.execute(uri);
        return true;
      }
      return super.shouldOverrideUrlLoading(view, url);
    }
  };

  private WebChromeClient mWebChromeClient = new WebChromeClient() {
    @Override
    public void onProgressChanged(WebView view, int newProgress) {
      super.onProgressChanged(view, newProgress);
      mActivity.setProgress(newProgress * 1000);
    }
  };

  /**
   * Get a request token from the Evernote web service and send the user
   * to a browser to authorize access.
   */
  private AsyncTask<Void, Void, String> mBeginAuthentication = new AsyncTask<Void, Void, String>() {

    @Override
    protected String doInBackground(Void... params) {
      String url = null;
      try {
        OAuthService service = createService();
        Log.i(TAG, "Retrieving OAuth request token...");
        Token reqToken = service.getRequestToken();
        mRequestToken = reqToken.getToken();
        mRequestTokenSecret = reqToken.getSecret();

        Log.i(TAG, "Redirecting user for authorization...");
        url = service.getAuthorizationUrl(reqToken);
      } catch (Exception ex) {
        Log.e(TAG, "Failed to obtain OAuth request token", ex);
      }
      return url;
    }

    /**
     * Open a webview to allow the user to authorize access to their account
     * @param url
     */
    @Override
    protected void onPostExecute(String url) {
      if (!TextUtils.isEmpty(url)) {
        mWebView.loadUrl(url);
      } else {
        exit(false);
      }
    }
  };

  /**
   * Async Task to complete the oauth process.
   */
  private AsyncTask<Uri, Void, EvernoteAuthToken> mCompleteAuthentication = new AsyncTask<Uri, Void, EvernoteAuthToken>() {

    @Override
    protected EvernoteAuthToken doInBackground(Uri... uris) {
      EvernoteAuthToken authToken = null;
      if(uris == null || uris.length == 0) {
        return null;
      }
      Uri uri = uris[0];

      if (!TextUtils.isEmpty(mRequestToken)) {
        OAuthService service = createService();
        String verifierString = uri.getQueryParameter("oauth_verifier");
        if (TextUtils.isEmpty(verifierString)) {
          Log.i(TAG, "User did not authorize access");
        } else {
          Verifier verifier = new Verifier(verifierString);
          Log.i(TAG, "Retrieving OAuth access token...");
          try {
            Token reqToken = new Token(mRequestToken, mRequestTokenSecret);
            authToken = new EvernoteAuthToken(service.getAccessToken(reqToken, verifier));
          } catch (Exception ex) {
            Log.e(TAG, "Failed to obtain OAuth access token", ex);
          }
        }
      } else {
        Log.d(TAG, "Unable to retrieve OAuth access token, no request token");
      }

      return authToken;
    }

    /**
     * Save the token and exit
     */

    @Override
    protected void onPostExecute(EvernoteAuthToken authToken) {

      if(authToken == null || EvernoteSession.getInstance() == null) {
        exit(false);
        return;
      }

      EvernoteSession.getInstance().persistAuthenticationToken(getApplicationContext(), authToken);
      exit(true);

    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    //Show web loading progress
    getWindow().requestFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.webview);
    mActivity = this;

    if (savedInstanceState != null) {
      mEvernoteHost = savedInstanceState.getString(EXTRA_EVERNOTE_HOST);
      mConsumerKey = savedInstanceState.getString(EXTRA_CONSUMER_KEY);
      mConsumerSecret = savedInstanceState.getString(EXTRA_CONSUMER_SECRET);
      mRequestToken = savedInstanceState.getString(EXTRA_REQUEST_TOKEN);
      mRequestTokenSecret = savedInstanceState.getString(EXTRA_REQUEST_TOKEN_SECRET);
    } else {
      Intent intent = getIntent();
      mEvernoteHost = intent.getStringExtra(EXTRA_EVERNOTE_HOST);
      mConsumerKey = intent.getStringExtra(EXTRA_CONSUMER_KEY);
      mConsumerSecret = intent.getStringExtra(EXTRA_CONSUMER_SECRET);
    }

    mWebView = (WebView) findViewById(R.id.webview);
    mWebView.setWebViewClient(mWebViewClient);
    mWebView.setWebChromeClient(mWebChromeClient);
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (TextUtils.isEmpty(mEvernoteHost) ||
        TextUtils.isEmpty(mConsumerKey) ||
        TextUtils.isEmpty(mConsumerSecret)) {
      exit(false);
      return;
    }

    if (mBeginAuthentication.getStatus() == AsyncTask.Status.PENDING) {
      mBeginAuthentication.execute();
    }
  }


  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putString(EXTRA_EVERNOTE_HOST, mEvernoteHost);
    outState.putString(EXTRA_CONSUMER_KEY, mConsumerKey);
    outState.putString(EXTRA_CONSUMER_SECRET, mConsumerSecret);
    outState.putString(EXTRA_REQUEST_TOKEN, mRequestToken);
    outState.putString(EXTRA_REQUEST_TOKEN_SECRET, mRequestTokenSecret);

    super.onSaveInstanceState(outState);
  }

  private String getCallbackScheme() {
    return "en-" + mConsumerKey;
  }

  @SuppressWarnings("unchecked")
  private OAuthService createService() {
    OAuthService builder = null;
    Class apiClass = EvernoteApi.class;

    String consumerKey;
    String consumerSecret;
    synchronized (mLock) {
      consumerKey = mConsumerKey;
      consumerSecret = mConsumerSecret;
    }

    if (mEvernoteHost.equals("sandbox.evernote.com")) {
      apiClass = EvernoteApi.Sandbox.class;
    } else if (mEvernoteHost.equals("app.yinxiang.com")) {
      apiClass = YinxiangApi.class;
    }
    builder = new ServiceBuilder()
        .provider(apiClass)
        .apiKey(consumerKey)
        .apiSecret(consumerSecret)
        .callback(getCallbackScheme() + "://callback")
        .build();

    return builder;
  }

  /**
   * Exits and toasts an error logging in
   */
  private void exit(final boolean success) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(mActivity, success ? R.string.evernote_login_successfull : R.string.evernote_login_failed, Toast.LENGTH_LONG).show();
        setResult(success ? RESULT_OK : RESULT_CANCELED);
        finish();
      }
    });
  }
}
