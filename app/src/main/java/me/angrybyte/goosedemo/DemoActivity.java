package me.angrybyte.goosedemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.angrybyte.goose.Article;
import me.angrybyte.goose.Configuration;
import me.angrybyte.goose.ContentExtractor;
import me.angrybyte.goose.network.GooseDownloader;

public class DemoActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = DemoActivity.class.getSimpleName();
    private static final String URL_REGEX = "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
    private static final Pattern PATTERN_URL = Pattern.compile(URL_REGEX, Pattern.CASE_INSENSITIVE);
    private static final int DETAILS_MAX_LENGTH = 100;

    private final class ArticleTask extends AsyncTask<String, Void, Pair<String[], Bitmap>> {

        private final Pair<String[], Bitmap> UNKNOWN_FAIL = new Pair<>(new String[]{"Unknown", "Unknown", "Unknown"}, null);
        private final Pair<String[], Bitmap> LOAD_FAIL = new Pair<>(new String[]{"Load failed", "Load failed", "Load fail"}, null);

        @Override
        protected Pair<String[], Bitmap> doInBackground(final String... strings) {
            String messageText = strings[0];

            Matcher urlMatcher = PATTERN_URL.matcher(messageText);

            if (!urlMatcher.find()) {
                Log.d(TAG, "No URL found");
                return UNKNOWN_FAIL;
            }

            String url = messageText.substring(urlMatcher.start(0), urlMatcher.end(0));
            Log.d(TAG, "Article extraction: found URL " + url);

            if (isCancelled()) {
                return UNKNOWN_FAIL;
            }

            Configuration config = new Configuration(getCacheDir().getAbsolutePath());
            ContentExtractor extractor = new ContentExtractor(config);
            Article article = extractor.extractContent(url);
            if (article == null) {
                return LOAD_FAIL;
            }
            String details = article.getCleanedArticleText();
            if (details == null) {
                details = article.getMetaDescription();
            } else {
                details = details.substring(0, Math.min(DETAILS_MAX_LENGTH, details.length() - 1));
            }

            if (isCancelled()) {
                return UNKNOWN_FAIL;
            }

            Bitmap photo = null;
            if (article.getTopImage() != null) {
                try {
                    photo = GooseDownloader.getPhoto(article.getTopImage().getImageSrc());
                } catch (Exception ignored) {
                }
            }

            if (isCancelled()) {
                return UNKNOWN_FAIL;
            }

            String[] results = new String[]{url, article.getTitle(), details};
            return new Pair<>(results, photo);
        }

        @Override
        protected void onPostExecute(final Pair<String[], Bitmap> articleData) {
            String[] strings = articleData.first;
            Bitmap photo = articleData.second;

            String urlText = getString(R.string.url, strings[0]);
            String titleText = getString(R.string.title, strings[1]);
            String detailsText = getString(R.string.details, strings[2]);

            mArticleUrl.setText(urlText);
            mArticleTitle.setText(titleText);
            mArticleDetails.setText(detailsText);
            mPhoto.setImageBitmap(photo);

            updateWorkResultUi(true, true);
        }

        @Override
        protected void onCancelled() {
            updateWorkResultUi(false, true);
        }

    }

    private Button mExtract;
    private CardView mResultsCard;
    private TextView mArticleUrl;
    private TextView mArticleTitle;
    private TextView mArticleDetails;
    private EditText mTextEntry;
    private ImageView mPhoto;

    private ArticleTask mArticleTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        mExtract = (Button) findViewById(R.id.action_extract);
        mArticleUrl = (TextView) findViewById(R.id.text_url);
        mArticleTitle = (TextView) findViewById(R.id.text_title);
        mArticleDetails = (TextView) findViewById(R.id.text_details);
        mTextEntry = (EditText) findViewById(R.id.text_entry);
        mPhoto = (ImageView) findViewById(R.id.photo);
        mResultsCard = (CardView) findViewById(R.id.result_group);

        mExtract.setOnClickListener(this);
        updateWorkResultUi(false, true);
    }

    @Override
    public void onClick(final View view) {
        stopTask();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            startTask();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startTask();
        }
    }

    private void updateWorkResultUi(boolean showResults, boolean enableButton) {
        mExtract.setEnabled(enableButton);
        mResultsCard.setVisibility(showResults ? View.VISIBLE : View.GONE);
    }

    private void startTask() {
        mArticleTask = new ArticleTask();
        String enteredText = mTextEntry.getText().toString();
        mArticleTask.execute(enteredText);

        Toast.makeText(this, R.string.please_wait, Toast.LENGTH_LONG).show();
        updateWorkResultUi(false, false);
    }

    private void stopTask() {
        if (mArticleTask != null) {
            mArticleTask.cancel(true);
            mArticleTask = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopTask();
    }

}
