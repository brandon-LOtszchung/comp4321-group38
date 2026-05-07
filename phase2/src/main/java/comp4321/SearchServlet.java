package comp4321;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet("/search")
public class SearchServlet extends HttpServlet {
    private Indexer indexer;
    private StopStem stopStem;
    private SearchEngine searchEngine;

    @Override
    public void init() throws ServletException {
        try {
            // Build paths from webapp root (getRealPath("/spider_db") returns null since that exact file doesn't exist)
            String webRoot = getServletContext().getRealPath("/");
            String dbPath = webRoot + "spider_db";
            String stopPath = webRoot + "stopwords.txt";

            this.indexer = new Indexer(dbPath);
            this.stopStem = new StopStem(stopPath);
            this.searchEngine = new SearchEngine(indexer, stopStem);
        } catch (Exception e) {
            throw new ServletException("Failed to initialize Search Engine components", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        String query = request.getParameter("query");

        try {
            List<SearchEngine.Result> results = searchEngine.search(query);

            // Send the results and the query terms to the Request Context
            request.setAttribute("results", results);
            request.setAttribute("query", query);

            // Forward to front-end JSP rendering
            request.getRequestDispatcher("/result.jsp").forward(request, response);

        } catch (Exception e) {
            throw new ServletException("Search execution error", e);
        }
    }

    @Override
    public void destroy() {
        try {
            if (indexer != null) {
                indexer.finalize(); // Release the JDBM archive lock
            }
        } catch (Throwable t) {
            log("Error closing indexer in servlet destroy", t);
        }
    }
}