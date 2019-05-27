package ph.farmwatch.audiolooptest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    Button button_record, button_play, button_sequence, button_reset;
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

    MediaPlayer mediaPlayer;
    List<Integer> sequence;
    Iterator<Integer> seq_iterator;

    enum State {READY, RECORD, PLAY, SEQUENCE};

    State state = State.READY;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private MediaRecorder recorder;
    String fileName;

    int countdown;

    ScheduledExecutorService executor_rec_legnth, executor_countdown;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        sequence = new ArrayList<>(SEQUENCE_SIZE);
        fileName = getCacheDir().getAbsolutePath() + "/test.3gp";

        textView_status = findViewById(R.id.textview_status);

        editText_answer = findViewById(R.id.edit_answer);

        button_record = findViewById(R.id.button_record);
        button_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                button_record.setEnabled(false);

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

                mediaPlayer = MediaPlayer.create(MainActivity.this, Uri.fromFile(new File(fileName)));
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        cleanUpMediaPlayer();

                        button_sequence.setEnabled(true);
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
                StringBuilder control = new StringBuilder(10);
                for(Integer i : sequence) {
                    control.append(i);
                }
                //textView_status.setText(control.toString());

                String answer = editText_answer.getText().toString();

                if(answer.contentEquals(control)) {
                    textView_status.setText("PASSED");
                } else {
                    textView_status.setText("FAILED");
                }
            }
        });

        button_reset = findViewById(R.id.button_reset);
        button_reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
            sequence.add(new Random().nextInt(10));
        }
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
