package com.parking.application.port.out;

import com.parking.domain.entity.AccessToken;
import com.parking.domain.entity.User;

public interface AccessTokenIssuerPort {

    AccessToken issueFor(User user);
}
