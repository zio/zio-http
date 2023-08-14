# API Documentation

Welcome to the API documentation for your project. This document provides details about the various endpoints available in your API.

## Get User Information

**Endpoint:** `/api/user/{user_id}`

**Description:**
This endpoint allows retrieving user information based on the provided user ID.

**Method:** `GET`

**Request:**
No specific request body is required for this endpoint. The user ID is provided as a path parameter.

**Response:**
A successful response will include user information in JSON format, including user ID, name, and email.

**Parameters:**
- `user_id` (Path Parameter): The ID of the user for whom information is requested.

**Headers:**
- `Accept: application/json` - Indicates that the client expects JSON response.

**Example Usage:**

Request:
```http
GET /api/user/123
```
Response:
```
Status: 200 OK
Body:
{
  "user_id": 123,
  "name": "John Doe",
  "email": "john.doe@example.com"
}
```
## Create New User Account
**Endpoint:** `/api/users/`

**Description**: 
This endpoint allows creating a new user.

**Method:** `POST`

**Request:**
The request body should contain user information in JSON format, including name and email.


**Response:**
A successful response will include the user's newly created ID.

**Headers:**
- `Content-Type: application/json` - Indicates that the server expects to receive data in JSON format from the client.

**Example Usage:**

Request:
```http
POST /api/user
Content-Type: application/json

Body:
{
  "name": "Alice Smith",
  "email": "alice@example.com"
}
```

Response:

```
Status: 201 Created
Body:
{
  "user_id": 124
}
```