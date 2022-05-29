import { render, screen } from '@testing-library/react';
import App from './App';
import {BrowserRouter} from "react-router-dom";
import React from 'react';
import {Circle, initialize, Map, Marker, mockInstances} from "@googlemaps/jest-mocks";
import { useAuth0 } from "@auth0/auth0-react";
import CreateDogProfile from "./components/CreateDogProfile";
import ViewDogProfile from "./components/ViewDogProfile";
import {dogProfile} from "./testdata";

/**
 * Testing Pet My Dog Login Heading Display
 */
test('heading-display', () => {
  render(
      <BrowserRouter>
        <App />
      </BrowserRouter>
  );
  const linkElement = screen.getByText(/Pet My Dog/i);
  expect(linkElement).toBeInTheDocument();
});

/**
 * Testing Google Oauth Login (Test not yet implemented)
 */
// test('google-oauth-login', () => {
//
// });
// const user = {
//     email: "johndoe@me.com",
//     email_verified: true,
//     sub: "google-oauth2|12345678901234",
// };
//
// jest.mock("@auth0/auth0-react");
//
// const mockedUseAuth0 = mocked(useAuth0, true);
//
// describe("Logged in", () => {
//     beforeEach(() => {
//         mockedUseAuth0.mockReturnValue({
//             isAuthenticated: true,
//             user,
//             logout: jest.fn(),
//             loginWithRedirect: jest.fn(),
//             getAccessTokenWithPopup: jest.fn(),
//             getAccessTokenSilently: jest.fn(),
//             getIdTokenClaims: jest.fn(),
//             loginWithPopup: jest.fn(),
//             isLoading: false,
//         });
//     });
// });

/**
 * Testing Google Maps Display
 */
beforeAll(() => {
    initialize();
});

test('mocking-google-maps', () => {
    const map = new google.maps.Map(null);
    const markerOne = new google.maps.Marker();
    const markerTwo = new google.maps.Marker();
    const circle = new google.maps.Circle();

    map.setHeading(8);
    markerOne.setMap(map);
    markerTwo.setLabel("My marker");
    circle.setMap(map);

    const mapMocks = mockInstances.get(Map);
    const markerMocks = mockInstances.get(Marker);
    const circleMocks = mockInstances.get(Circle);

    expect(mapMocks).toHaveLength(1);
    expect(markerMocks).toHaveLength(2);
    expect(mapMocks[0].setHeading).toHaveBeenCalledWith(8);
    expect(markerMocks[0].setMap).toHaveBeenCalledTimes(1);
    expect(markerMocks[1].setLabel).toHaveBeenCalledWith("My marker");
    expect(circleMocks[0].setMap).toHaveBeenCalledTimes(1);
});

// Clear all mocks
beforeEach(() => {
    mockInstances.clearAll();
});

// Clear specific mocks
beforeEach(() => {
    mockInstances.clear(Map, Marker);
});

/**
 * Testing Status Change of Meeting Requests after requests are
 * accepted or declined.
 */
test('request-status-change', () => {

});


/**
 * Test if the title is shown
 */
test('create-dog-text-shown', () => {
    render(
        <BrowserRouter>
            <CreateDogProfile />
        </BrowserRouter>
    );
    const profileText = screen.getByText(/Your Dog's Profile/i);
    expect(profileText).toBeInTheDocument;
});

/**
 * Test if the sample dogProfile is shown
 */
test ('dog-profile-shown', () => {
    render(
        <BrowserRouter>
            <ViewDogProfile dogProfile={dogProfile} />
        </BrowserRouter>
    );

    const title = screen.getByText(/Your Dog's Profile/i);
    expect(title).toBeInTheDocument;

    const newName = screen.getByDisplayValue(/andog1/i);
    expect(newName).toBeInTheDocument;

    const newAge = screen.getByDisplayValue(/7/i);
    expect(newAge).toBeInTheDocument;
} )

