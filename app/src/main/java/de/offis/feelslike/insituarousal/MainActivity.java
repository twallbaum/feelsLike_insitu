package de.offis.feelslike.insituarousal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity implements View.OnClickListener {

    private Intent intent;
    private PendingIntent pendingIntent;
    private AlarmManager alarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button starteStudie = (Button)findViewById(R.id.starteStudie);
        starteStudie.setOnClickListener(this);

        Button stopStudie = (Button)findViewById(R.id.stopStudie);
        stopStudie.setOnClickListener(this);

        Button arousal = (Button)findViewById(R.id.arousal);
        arousal.setOnClickListener(this);

        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        int userID = sharedPref.getInt("uid", 0);

        EditText txt = (EditText)this.findViewById(R.id.txtuid);
        txt.setText(userID+"");
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.starteStudie: {
                this.saveUid();
                InputService.start(this);

                Context context = getApplicationContext();
                CharSequence text = "study started";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();

                break;
            }
            case R.id.stopStudie: {
                InputService.stop(this);

                Context context = getApplicationContext();
                CharSequence text = "study ended";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();

                break;
            }
            case R.id.arousal: {
                Intent intent = new Intent(this, ArousalInput.class);
                startActivity(intent);
                break;
            }
        }
    }

    private void saveUid() {

        EditText txt = (EditText)this.findViewById(R.id.txtuid);
        int uid = Integer.parseInt(txt.getText().toString());

        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("uid", uid);
        editor.commit();
    }
}
