package com.geekholt.butterknife_demo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.geekholt.annotation.BindView;
import com.geekholt.butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.txt_main)
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        textView.setText("Hello ButterKnife");
    }
}
