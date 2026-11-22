package pro.pagel.training.android;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import okhttp3.OkHttpClient;

/** Minimale Activity, damit AppCompat und OkHttp tatsächlich verwendet werden. */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OkHttpClient client = new OkHttpClient.Builder().build();
        TextView text = new TextView(this);
        text.setText("Renovate-Training – OkHttp-Timeout: " + client.connectTimeoutMillis() + " ms");
        setContentView(text);
    }
}
