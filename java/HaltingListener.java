import com.omarathon.riotapicrawler.presets.listeners.DefaultCrawlerListener;

// a CrawlerListener which uses the DefaultCrawlerListener but produces a RuntimeException when it stops crawling
public class HaltingListener extends DefaultCrawlerListener {
    @Override
    public void onEndCrawl() {
        super.onEndCrawl();
        throw new RuntimeException("Stopped crawling on this crawler!!");
    }
}
