import React, { useState, useEffect } from 'react';
import {GoogleMap, Marker, useJsApiLoader} from '@react-google-maps/api';

const containerStyle = {
    width: "auto",
    height: "100vh"
};

function Map(props) {
    const [thisUser, setThisUser] = useState({
        lat: -3.745,
        lng: -38.523
    });
    const [thatUser, setThatUser] = useState(props.thatUser);

    useEffect(() => {
        navigator.geolocation.getCurrentPosition((position) => {
            let lat = position.coords.latitude;
            let lng = position.coords.longitude;
            setThisUser({lat, lng});
            console.log({lat, lng});
        });
    }, []);

    const { isLoaded } = useJsApiLoader({
        id: 'google-map-script',
        googleMapsApiKey: "AIzaSyBmy8ljB-id0qugtedezLUAc5o07UO8uwE"
    })

    const [map, setMap] = React.useState(null)

    const onLoad = React.useCallback(function callback(map) {
        const bounds = new window.google.maps.LatLngBounds(thisUser);
        map.fitBounds(bounds);
        setMap(map)
    }, [])

    const onUnmount = React.useCallback(function callback(map) {
        setMap(null)
    }, [])

    return isLoaded ? (
        <GoogleMap
            mapContainerStyle={containerStyle}
            center={thisUser}
            zoom={10}
            onLoad={onLoad}
            onUnmount={onUnmount}
        >
            <Marker
                key="1"
                position={thisUser}
            />
        </GoogleMap>
    ) : <></>
}

export default React.memo(Map)