package org.houxg.leamonax.ui;

import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.houxg.leamonax.R;
import org.houxg.leamonax.utils.DisplayUtils;
import org.houxg.leamonax.utils.ToastUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SearchActivity extends BaseActivity {

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.search)
    SearchView mSearchView;

    private NoteFragment mNoteFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        ButterKnife.bind(this);
        initToolBar(mToolbar, true);
        setTitle("");
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                NoteFragment.Mode mode = NoteFragment.Mode.SEARCH;
                mode.setKeywords(query);
                mNoteFragment.setMode(mode);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        mNoteFragment = NoteFragment.newInstance();
        mNoteFragment.setOnSearchFinishListener(new NoteFragment.OnSearchFinishListener() {
            @Override
            public void doSearchFinish() {
                ToastUtils.show(SearchActivity.this, R.string.activity_search_note_not_found);
                DisplayUtils.hideKeyboard(mSearchView);
            }
        });
        transaction.add(R.id.container, mNoteFragment);
        transaction.commit();

        ImageView searchCloseIcon = (ImageView) mSearchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        searchCloseIcon.setImageResource(R.drawable.ic_clear);
        final ImageView searchIcon = (ImageView) mSearchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
        mSearchView.post(new Runnable() {
            @Override
            public void run() {
                searchIcon.setImageDrawable(null);
                searchIcon.setVisibility(View.GONE);
            }
        });
        TextView searchAutoComplete = mSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchAutoComplete.setHintTextColor(getResources().getColor(R.color.menu_text));
        searchAutoComplete.setTextColor(getResources().getColor(R.color.menu_text));
        mSearchView.setIconified(false);
        mSearchView.setIconifiedByDefault(false);
    }
}
