import java.util.ArrayList;
import java.util.List;

public class PortfolioPractice {
    public static void main(String[] args) {
        List<Work> works = new ArrayList<>();
        works.add(new Work("城市雨夜", "上海"));
        works.add(new Work("山间清晨", "杭州"));
        works.add(new Work("1234", "杭州"));
        int count = 0;
        for (Work work : works){
            count++;
            if ("杭州".equals(work.getLocation())) {
                System.out.println(work.getTitle());
            }
        }
        System.out.println(count);
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
