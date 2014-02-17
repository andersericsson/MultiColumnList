package com.jayway.columnlist;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final MyAdapter adapter = new MyAdapter(this, 50);
        ColumnListView listView = (ColumnListView) findViewById(R.id.list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                Toast.makeText(MainActivity.this, "Clicked item: " + position, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class MyAdapter extends BaseAdapter {
        Context mContext;
        int mCount;
        ArrayList<Integer> mHeights = new ArrayList<Integer>();
        ArrayList<Integer> mColors = new ArrayList<Integer>();


        public MyAdapter(final Context context, int count) {
            mContext = context;
            mCount = count;
            for (int i = 0; i < mCount; i++) {
                mHeights.add((int) (Math.random() * 800 + 200));
                mColors.add(Color.rgb((int) (Math.random() * 0x80), (int) (Math.random() * 0x80), (int) (Math.random() * 0x80)));
            }
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public Object getItem(final int position) {
            return position;
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            TextView view = (TextView) convertView;
            if (view == null) {
                view = (TextView) LayoutInflater.from(mContext).inflate(R.layout.list_item, null, false);
            }
            view.setText("View " + position);
            AbsListView.LayoutParams params = (AbsListView.LayoutParams) view.getLayoutParams();

            if (params == null) {
                params = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, mHeights.get(position));
            } else {
                params.height = mHeights.get(position);
            }
            view.setLayoutParams(params);
            view.setBackgroundColor(mColors.get(position));

            return view;
        }
    }
}
