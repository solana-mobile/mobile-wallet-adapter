import React, {useRef, useEffect} from 'react';
import {Animated, Easing} from 'react-native';
import type {PropsWithChildren} from 'react';
import type {ViewStyle} from 'react-native';

type FadeInViewProps = PropsWithChildren<{style: ViewStyle, shown: boolean}>;

export default function FadeInView(props: FadeInViewProps) {
  const fadeAnim = useRef(new Animated.Value(0)).current; // Initial value for opacity: 0

  useEffect(() => {
    if (props.shown) {
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 350,
        easing: Easing.bezier(0.28, 0, 0.63, 1),
        useNativeDriver: true,
      }).start();
    } else {
      Animated.timing(fadeAnim, {
        toValue: 0,
        duration: 350,
        easing: Easing.cubic,
        useNativeDriver: true,
      }).start(); 
    }
  }, [fadeAnim]);

  return (
    <Animated.View // Special animatable View
      style={{
        ...props.style,
        opacity: fadeAnim, // Bind opacity to animated value
        transform: [{
            translateY: fadeAnim.interpolate({
              inputRange: [0, 1],
              outputRange: [150, 0]
            }),
          }],
      }}>
      {props.children}
    </Animated.View>
  );
};