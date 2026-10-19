package com.bcp.workshop.auth;

record StoredCredentials(UserRecord user, String passwordHash) {}