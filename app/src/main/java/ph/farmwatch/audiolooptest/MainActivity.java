package ph.farmwatch.audiolooptest;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    public static final String LOG_FILENAME = "audio-loop-test.txt";

    Button button_record, button_play, button_sequence, button_reset, button_open_logs, button_send_logs;
    TextView textView_status;
    EditText editText_answer;

    final int SEQUENCE_SIZE = 6;

    final int[] number_file = {
            R.raw.zero,
            R.raw.one,
            R.raw.two,
            R.raw.three,
            R.raw.four,
            R.raw.five,
            R.raw.six,
            R.raw.seven,
            R.raw.eight,
            R.raw.nine
    };

    final int[] BLACKLIST = {2, 4, 6, 8};

    MediaPlayer mediaPlayer;
    List<Integer> sequence;
    Iterator<Integer> seq_iterator;

    enum State {READY, RECORD, PLAY, SEQUENCE};

    State state = State.READY;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_PERMISSION = 200;
    private MediaRecorder recorder;
    String fileName;

    int countdown;
    int play_count = 0;
    int sequence_count = 0;

    ScheduledExecutorService executor_rec_legnth, executor_countdown;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_PERMISSION:
                permissionAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionAccepted) finish();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userLog("app launch");

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION);

        sequence = new ArrayList<>(SEQUENCE_SIZE);
        fileName = getFilesDir().getAbsolutePath() + "/test.3gp";

        textView_status = findViewById(R.id.textview_status);

        editText_answer = findViewById(R.id.edit_answer);

        button_record = findViewById(R.id.button_record);
        button_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                button_record.setEnabled(false);

                userLog("record");

                state = State.RECORD;
                countdown = 6;

                countdown();
            }
        });

        button_play = findViewById(R.id.button_play);
        button_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                button_play.setEnabled(false);
                textView_status.setText("Playing");

                play_count++;
                if(!canPlay()) {
                    Toast.makeText(MainActivity.this, "Exceeded Max Play Count", Toast.LENGTH_SHORT).show();
                    userLog("exceeded play count");
                    return;
                }

                userLog("play " + play_count);

                mediaPlayer = MediaPlayer.create(MainActivity.this, Uri.fromFile(new File(fileName)));
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        cleanUpMediaPlayer();

                        if(canSequence()) {
                            button_sequence.setEnabled(true);
                        }
                        button_play.setEnabled(true);
                        textView_status.setText("Done");
                    }
                });
                mediaPlayer.start();
            }
        });

        button_sequence = findViewById(R.id.button_sequence);
        button_sequence.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                button_sequence.setEnabled(false);
                button_play.setEnabled(false);

                sequence_count++;

                StringBuilder control = new StringBuilder(10);
                for(Integer i : sequence) {
                    control.append(i);
                }
                //textView_status.setText(control.toString());

                String answer = editText_answer.getText().toString();

                if(answer.contentEquals(control)) {
                    textView_status.setText("PASSED");
                    userLog("sequence passed");
                } else {
                    textView_status.setText("FAILED");
                    userLog("sequence failed");
                }
            }
        });

        button_reset = findViewById(R.id.button_reset);
        button_reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play_count = 0;
                sequence_count = 0;

                userLog("reset");

                if(mediaPlayer != null) {
                    if(mediaPlayer.isPlaying()) mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer = null;
                }

                if(recorder != null) {
                    recorder.stop();
                    recorder.release();
                    recorder = null;
                }

                if(executor_rec_legnth != null) {
                    executor_rec_legnth.shutdown();
                }

                if(executor_countdown != null) {
                    executor_countdown.shutdown();
                }

                button_record.setEnabled(true);
                button_play.setEnabled(false);
                button_sequence.setEnabled(false);
                textView_status.setText("Ready");
                editText_answer.setText("");
            }
        });

        button_open_logs = findViewById(R.id.button_open_logs);
        button_open_logs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File file = new File(getLogfileDirectory(), LOG_FILENAME);
                Uri contentUri = FileProvider.getUriForFile(MainActivity.this, "ph.farmwatch.audiolooptest.fileprovider", file);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(contentUri, "text/plain");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                startActivity(Intent.createChooser(intent, "Open Logfile with"));
            }
        });

        button_send_logs = findViewById(R.id.button_send_logs);
        button_send_logs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Uri logUri = FileProvider.getUriForFile(MainActivity.this, "ph.farmwatch.audiolooptest.fileprovider", new File(getLogfileDirectory(), LOG_FILENAME));
                Uri recordingUri = FileProvider.getUriForFile(MainActivity.this, "ph.farmwatch.audiolooptest.fileprovider", new File(getFilesDir(), "test.3gp" ));

                Intent intent = ShareCompat.IntentBuilder.from(MainActivity.this)
                        .setType("*/*")
                        .addStream(logUri)
                        .addStream(recordingUri)
                        .setChooserTitle("Share Files")
                        .createChooserIntent()
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                //todo: this is a hardcoded fix for granting permission when sharing via bluetooth
                getApplicationContext().grantUriPermission("com.android.bluetooth", logUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                getApplicationContext().grantUriPermission("com.android.bluetooth", recordingUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                //end of todo

                /*
                List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_ALL);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    grantUriPermission(packageName, logUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    grantUriPermission(packageName, recordingUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                */

                startActivity(intent);
            }
        });
    }

    @Override
    protected void onStop() {
        userLog("app exit");

        super.onStop();
    }

    private boolean canPlay() {
        return (play_count < 3) && (sequence_count < 1);
    }

    private boolean canSequence() {
        return (sequence_count < 1);
    }

    private File getLogfileDirectory() {
        return getFilesDir();
        //return Environment.getExternalStorageDirectory();
    }

    private void userLog(String string) {
        BufferedWriter out;
        File Root = getLogfileDirectory();
        try {
            Log.d(TAG, "user log can write " + Root.canWrite());
            if (Root.canWrite()) {
                File LogFile = new File(Root, LOG_FILENAME);
                FileWriter LogWriter = new FileWriter(LogFile, true);
                out = new BufferedWriter(LogWriter);

                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                StringBuilder sb = new StringBuilder();
                sb.append(sdf.format(calendar.getTime()));
                sb.append(": ");
                sb.append(string);
                sb.append("\r\n");

                out.write(sb.toString());
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cleanUpMediaPlayer() {
        if(mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void countdown() {
        if(countdown > 0) {
            executor_countdown = Executors.newSingleThreadScheduledExecutor();
            executor_countdown.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    if(countdown < 1) {
                        executor_countdown.shutdown();
                        record();
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                countdown--;
                                textView_status.setText("-" + countdown + "-");
                            }
                        });
                    }

                }
            }, 0, 1, TimeUnit.SECONDS);
        }
    }

    private void record() {
        generateSequence();
        playNext();

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
        } catch (IOException e) {
            //Log.e(LOG_TAG, "prepare() failed");
        }

        recorder.start();

        final long start = System.currentTimeMillis();
        executor_rec_legnth = Executors.newSingleThreadScheduledExecutor();
        executor_rec_legnth.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long now = System.currentTimeMillis();
                        float millis = (now - start) / 1000f;
                        String status = String.format(Locale.US, "%.3fs", millis);
                        textView_status.setText(status);
                    }
                });
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
    }

    private void generateSequence() {
        sequence.clear();
        for(int i = 0; i < SEQUENCE_SIZE; i++) {
            int r;
            boolean valid = true;
            do {
                valid = true;
                r = new Random().nextInt(10);
                for (int k : BLACKLIST) {
                    if (r == k - 1) {
                        valid = false;
                        break;
                    }
                }
            } while (!valid);

            sequence.add(r);
        }
        Log.i(TAG, sequence.toString());
        seq_iterator = sequence.iterator();
    }

    private void playNext() {
        if(seq_iterator.hasNext()) {
            mediaPlayer = MediaPlayer.create(MainActivity.this, number_file[seq_iterator.next()]);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    cleanUpMediaPlayer();
                    playNext();
                }
            });
            mediaPlayer.start();
        } else {
            if(executor_rec_legnth != null) {
                executor_rec_legnth.shutdown();
            }
            recorder.stop();
            recorder.release();
            recorder = null;

            button_play.setEnabled(true);
        }
    }

}
