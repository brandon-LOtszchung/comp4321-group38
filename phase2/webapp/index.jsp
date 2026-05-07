<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>COMP4321 Web Search Engine</title>
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; text-align: center; background-color: #f8f9fa; margin: 0; padding-top: 10%; }
        .search-box { background: white; padding: 40px; border-radius: 8px; display: inline-block; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
        input[type="text"] { width: 400px; padding: 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 16px; }
        input[type="submit"] { padding: 12px 24px; border: none; background-color: #007bff; color: white; border-radius: 4px; font-size: 16px; cursor: pointer; margin-left: 10px; }
        input[type="submit"]:hover { background-color: #0056b3; }
    </style>
</head>
<body>
<div class="search-box">
    <h1 style="color: #333; margin-bottom: 30px;">🔍 Web Search Engine</h1>
    <form action="search" method="get">
        <input type="text" name="query" placeholder="Type keywords to search..." required>
        <input type="submit" value="Search">
    </form>
</div>
</body>
</html>