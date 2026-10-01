import java.util.ArrayList;
import java.util.List;

public class PortfolioPractice {
    public static void main(String[] args) {
        List<Work> works = new ArrayList<>();
        works.add(new Work("城市雨夜", "上海"));
        works.add(new Work("山间清晨", "杭州"));

        for (Work work : works){
            if ("上海".equals(work.getLocation())) {
                System.out.println(work.getTitle());
            }
        }
    }
}

class Work {
    private final String title;
    private final String location;

    Work(String title, String location) {
        this.title = title;
        this.location = location;
    }

    String getTitle() {
        return title;
    }

    String getLocation() {
        return location;
    }
}
