<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="comp4321.SearchEngine.Result" %>
<%!
    // HTML-escape helper — prevents XSS wherever user-controlled strings are written into HTML
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
%>
<%

    List<Result> results = (List<Result>) request.getAttribute("results");
    String query = (String) request.getAttribute("query");
    if (query == null) query = "";
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Search Results</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; background-color: #fdfdfd; color: #333; }
        .header { border-bottom: 1px solid #eee; padding-bottom: 20px; margin-bottom: 20px; }
        .search-bar input[type="text"] { width: 350px; padding: 8px; font-size: 14px; }
        .search-bar input[type="submit"] { padding: 8px 15px; font-size: 14px; }
        .result-count { color: #777; font-size: 14px; margin-bottom: 20px; }
        .result-item { margin-bottom: 30px; background: white; padding: 20px; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); border: 1px solid #f0f0f0; }
        .title { font-size: 18px; color: #1a0dab; text-decoration: none; font-weight: bold; }
        .title:hover { text-decoration: underline; }
        .url { color: #006621; font-size: 13px; display: block; margin: 4px 0; word-break: break-all; }
        .meta-info { font-size: 13px; color: #666; background: #f8f9fa; padding: 8px 12px; border-radius: 4px; margin: 8px 0; }
        .keywords { font-size: 13px; color: #444; }
        .keyword-tag { background: #e9ecef; padding: 2px 6px; border-radius: 3px; margin-right: 5px; font-size: 12px; display: inline-block; }
        .links-section { font-size: 12px; color: #555; margin-top: 10px; padding-left: 10px; border-left: 2px solid #dee2e6; }
        .link-item { color: #0056b3; text-decoration: none; display: block; margin-bottom: 2px; }
        .link-item:hover { text-decoration: underline; }
    </style>
</head>
<body>

<div class="header">
    <div class="search-bar">
        <form action="search" method="get">
            <input type="text" name="query" value="<%= esc(query) %>" required>
            <input type="submit" value="Search Again">
        </form>
    </div>
</div>

<div class="result-count">
    Found <%= (results != null) ? results.size() : 0 %> results for "<b><%= esc(query) %></b>"
</div>

<% if (results != null && !results.isEmpty()) { %>
<% for (Result res : results) { %>
<div class="result-item">
    <a class="title" href="<%= esc(res.getUrl()) %>" target="_blank"><%= esc(res.getTitle()) %></a>
    <span class="url"><%= esc(res.getUrl()) %></span>

    <div class="meta-info">
        <b>Score:</b> <%= String.format("%.6f", res.getScore()) %> &nbsp;|&nbsp;
        <b>Last Modified:</b> <%= res.getLastMod() %> &nbsp;|&nbsp;
        <b>Page Size:</b> <%= res.getSize() %> Bytes
    </div>

    <div class="keywords">
        <b>Top 5 Keywords:</b>
        <% if (res.getTopWords().isEmpty()) { %>
        <span style="color: #999;">None</span>
        <% } else { %>
        <% for (String kw : res.getTopWords()) { %>
        <span class="keyword-tag"><%= esc(kw) %></span>
        <% } %>
        <% } %>
    </div>

    <div class="links-section">
        <strong>Parent Links:</strong>
        <% if (res.getParentUrls().isEmpty()) { %>
        <span style="color:#999;">None</span>
        <% } else { %>
        <div style="margin-left: 10px;">
            <% for (String pUrl : res.getParentUrls()) { %>
            <a class="link-item" href="<%= esc(pUrl) %>" target="_blank">• <%= esc(pUrl) %></a>
            <% } %>
        </div>
        <% } %>

        <strong style="display:inline-block; margin-top:5px;">Child Links:</strong>
        <% if (res.getChildUrls().isEmpty()) { %>
        <span style="color:#999;">None</span>
        <% } else { %>
        <div style="margin-left: 10px;">
            <% for (String cUrl : res.getChildUrls()) { %>
            <a class="link-item" href="<%= esc(cUrl) %>" target="_blank">• <%= esc(cUrl) %></a>
            <% } %>
        </div>
        <% } %>
    </div>
</div>
<% } %>
<% } else { %>
<p style="text-align: center; color: #999; margin-top: 50px;">No matching documents found. Please try different keywords.</p>
<% } %>

<p style="text-align: center; margin-top: 50px;"><a href="index.jsp" style="color: #007bff; text-decoration: none;">← Back to Home</a></p>
</body>
</html>